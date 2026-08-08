package com.oryxos.core.react;

/** Token 用量信息。 */
public record Usage(int promptTokens, int completionTokens, int totalTokens) {

  public static final Usage EMPTY = new Usage(0, 0, 0);
}
