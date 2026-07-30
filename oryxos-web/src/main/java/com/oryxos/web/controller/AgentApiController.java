package com.oryxos.web.controller;

import com.oryxos.core.AgentService;
import com.oryxos.core.session.Session;
import com.oryxos.web.dto.ApiResponse;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Agent 调用 Controller(无状态调用)。
 */
@RestController
@RequestMapping("/api/v1/agents")
public class AgentApiController {

    private final AgentService agentService;

    public AgentApiController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/{name}/invoke")
    public ApiResponse<Map<String, String>> invoke(@PathVariable String name, @RequestBody Map<String, String> body) {
        String user = body.getOrDefault("user_id", "anonymous");
        String message = body.get("content");
        String sessionId = "web+" + user + "+" + name;
        Session session = new Session(sessionId, name, "web", user);
        String reply = agentService.process(session, message);
        return ApiResponse.ok(Map.of("reply", reply));
    }
}
