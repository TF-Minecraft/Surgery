package net.tfminecraft.surgery;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.mockbukkit.mockbukkit.MockBukkit;
import net.tfminecraft.tlibs.character.*;

/** Real shared facade with a controllable provider for lifecycle regression tests. */
public final class TestCharacters implements CharacterIdentity, AutoCloseable {
    private final Plugin plugin = MockBukkit.createMockPlugin("SurgeryIdentityTest");
    private final Map<UUID, CharacterSession> sessions = new HashMap<>();
    private long next;

    public TestCharacters() {
        Bukkit.getServicesManager().register(CharacterIdentity.class, this, plugin, ServicePriority.Normal);
    }
    public CharacterSession activate(Player player) { return activate(player, UUID.randomUUID()); }
    public CharacterSession activate(Player player, UUID id) {
        CharacterSession token = new CharacterSession(new CharacterRef(player.getUniqueId(), id), ++next);
        sessions.put(player.getUniqueId(), token);
        return token;
    }
    public CharacterSession invalidate(Player player) { return sessions.remove(player.getUniqueId()); }
    public void unregister() { Bukkit.getServicesManager().unregister(CharacterIdentity.class, this); }
    @Override public void close() { unregister(); }
    @Override public Optional<CharacterSession> currentSession(Player player) { return Optional.ofNullable(sessions.get(player.getUniqueId())); }
    @Override public boolean isCurrent(CharacterSession token) { return token.equals(sessions.get(token.ref().ownerId())); }
    @Override public Optional<CharacterInfo> info(UUID id) { return Optional.empty(); }
    @Override public Optional<List<CharacterInfo>> characters(UUID owner) { return Optional.empty(); }
    @Override public Optional<CharacterInfo> lastPlayed(UUID owner) { return Optional.empty(); }
    @Override public String displayName(Player player) { return player.getName(); }
}
