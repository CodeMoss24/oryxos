package com.oryxos.storage.config;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.identity.IdentityColumnSupport;
import org.hibernate.dialect.identity.IdentityColumnSupportImpl;

/**
 * SQLite 的 Hibernate 方言。
 *
 * <p>Hibernate 没有内置 SQLite 方言,这里用一个最小自实现 Dialect 让 Spring Data JPA 能建表和基本 CRUD。 核心阶段只要求能 CREATE
 * TABLE 和 INSERT/UPDATE/DELETE,复杂 SQL 方言特性不依赖。
 *
 * <p>通过 application.yaml 的
 * spring.jpa.properties.hibernate.dialect=com.oryxos.storage.config.SQLiteDialect 启用。
 *
 * <p>SQLite 的 ALTER TABLE 能力有限,hibernate.ddl-auto=update 不要用于表结构演进, 需手动维护建表脚本或引入 Flyway/Liquibase。
 */
public class SQLiteDialect extends Dialect {

  public SQLiteDialect() {
    super(DatabaseVersion.make(3, 46));
  }

  @Override
  public IdentityColumnSupport getIdentityColumnSupport() {
    return new IdentityColumnSupportImpl() {
      @Override
      public boolean supportsIdentityColumns() {
        return true;
      }

      @Override
      public String getIdentityColumnString(int type) {
        return "integer";
      }

      @Override
      public boolean hasDataTypeInIdentityColumn() {
        return false;
      }
    };
  }

  @Override
  public boolean supportsIfExistsBeforeTableName() {
    return true;
  }

  @Override
  public boolean dropConstraints() {
    return false;
  }
}
