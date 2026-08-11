package com.oryxos.tool.sandbox;

/** 沙箱动作类型,四值(文件读 / 文件写 / Shell 命令 / HTTP 请求)。文件读写分开,便于未来按读/写分权限。 */
public enum ActionType {
  FILE_READ,
  FILE_WRITE,
  SHELL_COMMAND,
  HTTP_REQUEST
}
