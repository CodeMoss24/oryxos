package com.oryxos.tool.sandbox;

/**
 * 表达"在受控环境里执行一个动作"的意图。只有 type 和 target 两个字段, 没有任何"白名单""容器""镜像"字样——接口表达意图,不表达实现。 target
 * 是纯字符串,具体是路径、命令还是 URL 由 type 决定,实现类自己去解释。
 */
public record SandboxAction(ActionType type, String target) {}
