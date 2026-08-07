# Changelog

All notable changes to this project are documented in this file.

## [5.12.7] - 2026-08-07

### Added
- Support `Decimal5` and `TinyDecimal5` in Hibernate type mapping, stored as `decimal(19,5)` and `decimal(10,5)` columns
- Document `Conditions.split` and `Operator` parsing rules

### Changed
- Upgrade build to sbt 2.x (bare settings, sbt 2 compatible plugins)
- Update `beangle-jdbc` to 1.1.12

## [5.12.6] - 2026-06-17

### Changed
- Rebase on `beangle-commons` 6.2.1

## [5.12.5] - 2026-06-15

### Changed
- Update to `beangle-commons` 6.2.0
- Adapt Hibernate 7.4.1

### Fixed
- Use `Json.deepCopy` for mutable JSON type

## [5.12.4] - 2026-06-03

### Added
- Support mutable JSON type

## [5.12.3] - 2026-06-01

### Added
- Support expression evaluation by path in OQL builder

## [5.12.2] - 2026-04-12

### Changed
- Replace `synchronized` with `ReentrantLock` in session management

## [5.12.1] - 2026-03-11

### Added
- Add `EntityDao.isDirty` method

## [5.12.0] - 2026-02-16

### Changed
- Split project into `beangle-data-model` and `beangle-data-hibernate` modules
- Support transactions based on Spring AOP directly
- Change ORM to JPA style
- Update to `beangle-jdbc` 1.1.8
- Update to parent 0.15.11

## [5.11.8] - 2026-02-06

### Changed
- Change `DataLogger` scope to `private[data]`
- Use environment properties for configuration

## [5.11.7] - 2026-02-05

### Changed
- Update to `XmlConfigs` for XML configuration
- Update build plugin 0.0.20

### Fixed
- Fix bug when container is missing

## [5.11.6] - 2026-01-27

### Changed
- Update to `beangle-commons` 5.8.1

## [5.11.5] - 2026-01-13

### Changed
- Update to `beangle-commons` 5.7.0

## [5.11.4] - 2025-12-07

### Changed
- Update to `beangle-commons` 5.6.33
- Rename `AccessProxy` to `AccessTracker`, generate `AccessTracker` on bind
- Fetch factories when context started

## [5.11.3] - 2025-12-04

### Added
- Add new `SessionCleaner`
- Simplify `newSession` and `newSessionHolder`

### Changed
- Shorten `HibernateTransactionManager` variable names

## [5.11.2] - 2025-11-27

### Changed
- Update Hibernate to 7.2.0.CR3

## [5.11.1] - 2025-11-24

### Added
- Add `update` function to `TemporalX` traits
- Support group functions in OQL builder
- Support `groupBy` and `orderBy` in new style
- Add `SessionHolder.clear` method

### Changed
- Adapt Hibernate 7
- Update to parent 0.15.2

### Fixed
- Fix `bindResource` executed twice
- Fix `AccessProxy` returning wrong class in multiple threads

## [5.11.0] - 2025-11-04

### Added
- Support `beangle.xml` configuration
- Add region name restriction for cache regions

### Changed
- Move `beangle.xml` to resources

## [5.10.0] - 2025-10-27

### Added
- Support expression in `OqlBuilder`
- Support method returning primitive in interface
- Add `evict` single entity by id

### Changed
- Replace javassist with bytebuddy
- Adapt Hibernate 7.2
- Update to parent 0.15.0

## [5.9.2] - 2025-08-23

### Added
- Support JSON mapping

## [5.9.1] - 2025-08-13

### Changed
- Restore to Hibernate 6.6.19

### Fixed
- Fix regression introduced while adapting Hibernate 7

## [5.9.0] - 2025-07-27

### Changed
- Adapt Hibernate 7
- Update to parent 0.14.1
