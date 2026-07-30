package com.oryxos.tool.sandbox;

/**
 * Sandbox 抽象接口。表达"在受控环境里执行一个动作"这个意图,
 * 不携带任何一档实现特有的概念(不出现"白名单""容器镜像""VM 配置"字样)。
 *
 * <p>用最重的 microVM 实现去反向套这个签名,也应该能干净套入,这是校验接口是否中立的办法。
 */
public interface Sandbox {

    void enforce(SandboxAction action) throws SandboxViolationException;

    record SandboxAction(ActionType type, String target) {}

    enum ActionType {
        FILE_READ,
        FILE_WRITE,
        SHELL_COMMAND,
        HTTP_REQUEST
    }
}
