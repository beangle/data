/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.data.orm.hibernate.cfg;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.type.BasicType;
import org.hibernate.type.BasicTypeRegistry;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserType;

public class BasicTypeContributions implements TypeContributions {

  private BasicTypeRegistry registry;
  private BootstrapContext ctx;

  public BasicTypeContributions(BasicTypeRegistry basicTypeRegistry, BootstrapContext ctx) {
    this.registry = basicTypeRegistry;
    this.ctx = ctx;
  }

  public void contributeType(BasicType t) {
    registry.register(t);
  }

  public void contributeType(BasicType t, String... keys) {
    registry.register(t, keys);
  }

  public void contributeType(UserType t, String... keys) {
    registry.register(t, keys);
  }

  public void contributeType(CompositeUserType t, String... keys) {
    registry.register(t, keys);
  }

  public void contributeJavaTypeDescriptor(JavaTypeDescriptor descriptor) {
    getTypeConfiguration().getJavaTypeDescriptorRegistry().addDescriptor(descriptor);
  }

  public void contributeSqlTypeDescriptor(SqlTypeDescriptor descriptor) {
    getTypeConfiguration().getSqlTypeDescriptorRegistry().addDescriptor(descriptor);
  }

  public TypeConfiguration getTypeConfiguration() {
    return ctx.getTypeConfiguration();
  }

}
