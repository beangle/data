# sbt 2 CAS jar 隐患（fullClasspath 返回旧 jar）

> 结论：sbt 2 下，`show <proj>/Compile/fullClasspath` 对**本仓库子项目**返回的是
> **CAS 内容寻址 jar**，而不是 `classes` 目录。`compile` 只更新 `classes` 与
> `resource_managed`，**不会重建 jar**；不先 `packageBin` 就直接用 `fullClasspath`
> 跑 JVM/native 构建，会拿到旧 classes。

## 现象

- `target/out/jvm/u/<proj>/<proj>-<version>.jar` 不是普通文件，而是符号链接：

  ```bash
  $ ls -l target/out/jvm/u/beangle-data-sample-native/beangle-data-sample-native-5.12.8-SNAPSHOT.jar
  lrwxrwxrwx ... -> /home/chaostone/.cache/sbt/v2/cas/sha256-dc0f4a2ea34d44a19ee54119e257a1f3f1c9acd98bf5ff738b25bbab5f7aafa5-61518
  ```

- `show sampleNative/Compile/fullClasspath` 的输出形如：

  ```
  * Attributed(${OUT}/jvm/u/beangle-data-sample-native/beangle-data-sample-native-5.12.8-SNAPSHOT.jar>sha256-dc0f4a2ea.../61518)
  ```

  其中 `>sha256-.../N` 是 CAS 对象的标注，去掉后得到的路径就是上面的符号链接，
  实际内容在 `~/.cache/sbt/v2/cas/sha256-...`。

## 根因

sbt 2 引入 CAS（content-addressed storage）缓存产物：jar 打包后按内容哈希放入
`~/.cache/sbt/v2/cas/`，`target/out/.../*.jar` 仅是指向该对象的符号链接。
`Compile / compile` 的产物是 `classes`/`resource_managed`，与 jar 解耦；
只有 `Compile / packageBin` 才会重新打包并生成新的 CAS 对象、更新符号链接。
因此“改了代码 → 只 compile → 用 fullClasspath”拿到的仍是上次 packageBin 的旧 jar。

## 实际踩坑

`samples/native` 中把 `eleColumn("value")` 改为 `eleColumn("tag_value")` 后，
只 `compile` 便直接跑 JVM/native，生成的 DDL 仍旧是旧的 `value` 列名；
`packageBin` 后再跑才生效。native 构建报 “classes 不全” 也多与此相关：
`AotPlugin`/`MetaPlugin`/`ProxyPlugin` 的产物落在 `resource_managed`，
不打进 jar 就不会出现在 `fullClasspath` 里。

## 规避方式

在取 `fullClasspath` 之前显式打包依赖子项目：

```bash
sbt -batch "model/Compile/packageBin; hibernate/Compile/packageBin; sampleNative/Compile/packageBin"
sbt -batch "show sampleNative/Compile/fullClasspath"
```

`samples/native/build-native.sh` 已内置该顺序（Step 1 先 packageBin 再 show）。
`build-native.sh` 的 classpath 解析里对 `>sha256-.../N` 的 `sed` 去除是必要的：
不剥离的话，该标注会让后面的路径解析失败。

## 其他影响面

- 任何直接消费 `fullClasspath` 的脚本（native-image-agent 采集、`build-native.sh`
  等价物、外部 JVM 启动）都受此影响，需先 packageBin。
- `sbt run`/`sbt test` 走的是 `classes` 目录，不经过 jar，不受影响。
- 外部依赖（`${CSR_CACHE}`/maven 本地仓的 jar）内容不变，无需担心；
  只有**本仓库子项目**的 jar 需要每次变更后刷新。
