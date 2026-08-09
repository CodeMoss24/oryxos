package com.oryxos.storage.config;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;

/**
 * Instant 与 ISO-8601 字符串的转换。SQLite 最小 Dialect 下 Hibernate 默认 TIMESTAMP_UTC 的 getTimestamp 解析不了 ISO
 * 格式(真实 SELECT 必炸),统一按字符串存取,读写语义不变。
 */
@Converter
public class InstantStringConverter implements AttributeConverter<Instant, String> {

  @Override
  public String convertToDatabaseColumn(Instant attribute) {
    return attribute == null ? null : attribute.toString();
  }

  @Override
  public Instant convertToEntityAttribute(String dbData) {
    return dbData == null ? null : Instant.parse(dbData);
  }
}
