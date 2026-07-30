package com.oryxos.core.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Profile 是底座内部的运行时宿主配置对象,决定一个 Agent "怎么跑"。
 * 由 AgentLoader.deriveProfile() 从 AGENT.md 的 frontmatter 派生,不是手写的 YAML。
 */
public class Profile {

    private String name;
    private String description;
    private Identity identity;
    private Provider provider;
    private List<String> tools = new ArrayList<>();
    private List<String> skills = new ArrayList<>();
    private List<String> mcpServers = new ArrayList<>();
    private List<ChannelBinding> channels = new ArrayList<>();
    private List<NotifyChannel> notifyChannels = new ArrayList<>();
    private List<Schedule> schedules = new ArrayList<>();
    private List<String> bootstrap = new ArrayList<>();
    private Settings settings = new Settings();

    public record Identity(String agentName, String prompt) {}
    public record Provider(String name, String model, Double temperature) {}
    public record ChannelBinding(String name, Map<String, Object> config) {}
    public record NotifyChannel(String type, Map<String, String> config) {}
    public record Schedule(String cron, String zone, String message) {}

    public static class Settings {
        private int maxIterations = 10;
        private int maxHistoryTurns = 20;

        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { this.maxIterations = maxIterations; }
        public int getMaxHistoryTurns() { return maxHistoryTurns; }
        public void setMaxHistoryTurns(int maxHistoryTurns) { this.maxHistoryTurns = maxHistoryTurns; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Identity getIdentity() { return identity; }
    public void setIdentity(Identity identity) { this.identity = identity; }
    public Provider getProvider() { return provider; }
    public void setProvider(Provider provider) { this.provider = provider; }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools; }
    public List<String> getSkills() { return skills; }
    public void setSkills(List<String> skills) { this.skills = skills; }
    public List<String> getMcpServers() { return mcpServers; }
    public void setMcpServers(List<String> mcpServers) { this.mcpServers = mcpServers; }
    public List<ChannelBinding> getChannels() { return channels; }
    public void setChannels(List<ChannelBinding> channels) { this.channels = channels; }
    public List<NotifyChannel> getNotifyChannels() { return notifyChannels; }
    public void setNotifyChannels(List<NotifyChannel> notifyChannels) { this.notifyChannels = notifyChannels; }
    public List<Schedule> getSchedules() { return schedules; }
    public void setSchedules(List<Schedule> schedules) { this.schedules = schedules; }
    public List<String> getBootstrap() { return bootstrap; }
    public void setBootstrap(List<String> bootstrap) { this.bootstrap = bootstrap; }
    public Settings getSettings() { return settings; }
    public void setSettings(Settings settings) { this.settings = settings; }
}
