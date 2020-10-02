package cn.nukkit;

import cn.nukkit.AdventureSettings.Type;
import cn.nukkit.block.*;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.blockentity.BlockEntitySpawnable;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.entity.*;
import cn.nukkit.entity.data.*;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.mob.EntityEnderman;
import cn.nukkit.entity.passive.EntityHorseBase;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.entity.passive.EntityPig;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityThrownTrident;
import cn.nukkit.event.block.ItemFrameDropItemEvent;
import cn.nukkit.event.block.WaterFrostEvent;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.EntityDamageEvent.DamageModifier;
import cn.nukkit.event.inventory.InventoryCloseEvent;
import cn.nukkit.event.inventory.InventoryPickupArrowEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.event.player.PlayerAsyncPreLoginEvent.LoginResult;
import cn.nukkit.event.player.PlayerInteractEvent.Action;
import cn.nukkit.event.player.PlayerTeleportEvent.TeleportCause;
import cn.nukkit.event.server.DataPacketReceiveEvent;
import cn.nukkit.event.server.DataPacketSendEvent;
import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.inventory.transaction.EnchantTransaction;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.data.ReleaseItemData;
import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.inventory.transaction.data.UseItemOnEntityData;
import cn.nukkit.item.*;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.food.Food;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.*;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.particle.PunchBlockParticle;
import cn.nukkit.level.sound.ExperienceOrbSound;
import cn.nukkit.math.*;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.NetworkInventoryAction;
import cn.nukkit.permission.PermissibleBase;
import cn.nukkit.permission.Permission;
import cn.nukkit.permission.PermissionAttachment;
import cn.nukkit.permission.PermissionAttachmentInfo;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.*;
import co.aikar.timings.Timing;
import co.aikar.timings.Timings;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.math3.util.FastMath;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteOrder;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The Player class
 *
 * @author MagicDroidX &amp; Box
 * Nukkit Project
 */
@Log4j2
public class Player extends EntityHuman implements CommandSender, InventoryHolder, ChunkLoader, IPlayer {

    public static final int SURVIVAL = 0;
    public static final int CREATIVE = 1;
    public static final int ADVENTURE = 2;
    public static final int SPECTATOR = 3;
    public static final int VIEW = SPECTATOR;

    public static final int CRAFTING_SMALL = 0;
    public static final int CRAFTING_BIG = 1;
    public static final int CRAFTING_ANVIL = 2;
    public static final int CRAFTING_ENCHANT = 3;
    public static final int CRAFTING_BEACON = 4;

    public static final float DEFAULT_SPEED = 0.1f;
    public static final float MAXIMUM_SPEED = 0.5f;

    public static final int PERMISSION_CUSTOM = 3;
    public static final int PERMISSION_OPERATOR = 2;
    public static final int PERMISSION_MEMBER = 1;
    public static final int PERMISSION_VISITOR = 0;

    public static final int ANVIL_WINDOW_ID = 2;
    public static final int ENCHANT_WINDOW_ID = 3;
    public static final int BEACON_WINDOW_ID = 4;

    protected final SourceInterface interfaz;

    public boolean playedBefore;
    public boolean spawned = false;
    public boolean loggedIn = false;
    public int gamemode;
    public long lastBreak = -1;
    private BlockVector3 lastBreakPosition = new BlockVector3();

    protected int windowCnt = 4;

    protected final BiMap<Inventory, Integer> windows = HashBiMap.create();
    protected final BiMap<Integer, Inventory> windowIndex = windows.inverse();
    protected final Set<Integer> permanentWindows = new IntOpenHashSet();

    public Vector3 speed = null;

    public final HashSet<String> achievements = new HashSet<>();

    public int craftingType = CRAFTING_SMALL;

    protected PlayerUIInventory playerUIInventory;
    protected CraftingGrid craftingGrid;
    protected CraftingTransaction craftingTransaction;
    protected EnchantTransaction enchantTransaction;

    protected long randomClientId;

    protected Vector3 forceMovement = null;

    protected Vector3 teleportPosition = null;

    protected boolean connected = true;
    protected final InetSocketAddress socketAddress;
    protected boolean removeFormat = true;

    protected String username;
    protected String iusername;
    protected String displayName;

    public int protocol = 999;
    public int raknetProtocol;
    protected String version;

    protected int startAction = -1;

    protected Vector3 sleeping = null;

    private int loaderId;

    public final Map<Long, Boolean> usedChunks = new Long2ObjectOpenHashMap<>();

    protected int spawnChunkLoadCount = 0;
    protected final Long2ObjectLinkedOpenHashMap<Boolean> loadQueue = new Long2ObjectLinkedOpenHashMap<>();
    protected int nextChunkOrderRun = 1;

    protected final Map<UUID, Player> hiddenPlayers = new HashMap<>();

    protected Vector3 newPosition = null;

    protected int chunkRadius;
    protected int viewDistance;

    protected Position spawnPosition;

    protected int inAirTicks = 0;
    protected int startAirTicks = 10;

    protected AdventureSettings adventureSettings;

    protected boolean checkMovement = true;

    //private final Map<Integer, List<DataPacket>> batchedPackets = new TreeMap<>();

    private final List<DataPacket> batchedPackets = new ArrayList<>();

    private PermissibleBase perm;

    private int exp = 0;
    private int expLevel = 0;

    protected PlayerFood foodData = null;

    private Entity killer = null;

    private final AtomicReference<Locale> locale = new AtomicReference<>(null);

    private int hash;

    private String buttonText = "Button";

    protected boolean enableClientCommand = true;

    private BlockEnderChest viewingEnderChest = null;

    private LoginChainData loginChainData;

    public Block breakingBlock = null;

    public int pickedXPOrb = 0;
    private boolean canPickupXP = true;

    protected int formWindowCount = 0;
    public Map<Integer, FormWindow> formWindows = new Int2ObjectOpenHashMap<>();
    protected Map<Integer, FormWindow> serverSettings = new Int2ObjectOpenHashMap<>();

    protected Map<Long, DummyBossBar> dummyBossBars = new Long2ObjectLinkedOpenHashMap<>();

    private AsyncTask preLoginEventTask = null;
    protected boolean shouldLogin = false;

    private int lastEnderPearl = 20;
    private int lastChorusFruitTeleport = 20;
    public long lastSkinChange = -1;
    private double lastRightClickTime = 0.0;
    private Vector3 lastRightClickPos = null;
    public EntityFishingHook fishing = null;
    public boolean formOpen;
    public boolean locallyInitialized;
    private boolean foodEnabled = true;
    private int failedTransactions;
    public int ticksSinceLastRest;

    private static final List<Byte> beforeLoginAvailablePackets = Arrays.asList(ProtocolInfo.BATCH_PACKET, ProtocolInfo.LOGIN_PACKET, ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET, ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET, ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET, ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET, ProtocolInfo.CLIENT_CACHE_STATUS_PACKET, ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET);

    public int getStartActionTick() {
        return startAction;
    }

    public void startAction() {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, true);
        this.startAction = this.server.getTick();
    }

    public void stopAction() {
        this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);
        this.startAction = -1;
    }

    public int getLastEnderPearlThrowingTick() {
        return lastEnderPearl;
    }

    public void onThrowEnderPearl() {
        this.lastEnderPearl = this.server.getTick();
    }

    public int getLastChorusFruitTeleport() {
        return lastChorusFruitTeleport;
    }

    public void onChorusFruitTeleport() {
        this.lastChorusFruitTeleport = this.server.getTick();
    }

    public BlockEnderChest getViewingEnderChest() {
        return viewingEnderChest;
    }

    public void setViewingEnderChest(BlockEnderChest chest) {
        if (chest == null && this.viewingEnderChest != null) {
            this.viewingEnderChest.getViewers().remove(this);
        } else if (chest != null) {
            chest.getViewers().add(this);
        }
        this.viewingEnderChest = chest;
    }

    public TranslationContainer getLeaveMessage() {
        return new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.left", this.displayName);
    }

    /**
     * This might disappear in the future.
     * Please use getUniqueId() instead (IP + clientId + name combo, in the future it'll change to real UUID for online auth)
     * @return random client id
     */
    public Long getClientId() {
        return randomClientId;
    }

    @Override
    public boolean isBanned() {
        return this.server.getNameBans().isBanned(this.username);
    }

    @Override
    public void setBanned(boolean value) {
        if (value) {
            this.server.getNameBans().addBan(this.username, null, null, null);
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "You are banned!");
        } else {
            this.server.getNameBans().remove(this.username);
        }
    }

    @Override
    public boolean isWhitelisted() {
        return this.server.isWhitelisted(this.username.toLowerCase());
    }

    @Override
    public void setWhitelisted(boolean value) {
        if (value) {
            this.server.addWhitelist(this.username.toLowerCase());
        } else {
            this.server.removeWhitelist(this.username.toLowerCase());
        }
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public Long getFirstPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("firstPlayed") : null;
    }

    @Override
    public Long getLastPlayed() {
        return this.namedTag != null ? this.namedTag.getLong("lastPlayed") : null;
    }

    @Override
    public boolean hasPlayedBefore() {
        return this.playedBefore;
    }

    public AdventureSettings getAdventureSettings() {
        return adventureSettings;
    }

    public void setAdventureSettings(AdventureSettings adventureSettings) {
        this.adventureSettings = adventureSettings.clone(this);
        this.adventureSettings.update();
    }

    public void resetInAirTicks() {
        this.inAirTicks = 0;
    }

    public void setAllowFlight(boolean value) {
        this.adventureSettings.set(Type.ALLOW_FLIGHT, value);
        this.adventureSettings.update();
    }

    public boolean getAllowFlight() {
        return this.adventureSettings.get(Type.ALLOW_FLIGHT);
    }

    public void setAllowModifyWorld(boolean value) {
        this.adventureSettings.set(Type.WORLD_IMMUTABLE, !value);
        this.adventureSettings.set(Type.BUILD_AND_MINE, value);
        this.adventureSettings.set(Type.WORLD_BUILDER, value);
        this.adventureSettings.update();
    }

    public void setAllowInteract(boolean value) {
        setAllowInteract(value, value);
    }

    public void setAllowInteract(boolean value, boolean containers) {
        this.adventureSettings.set(Type.WORLD_IMMUTABLE, !value);
        this.adventureSettings.set(Type.DOORS_AND_SWITCHED, value);
        this.adventureSettings.set(Type.OPEN_CONTAINERS, containers);
        this.adventureSettings.update();
    }

    public void setAutoJump(boolean value) {
        this.adventureSettings.set(Type.AUTO_JUMP, value);
        this.adventureSettings.update();
    }

    public boolean hasAutoJump() {
        return this.adventureSettings.get(Type.AUTO_JUMP);
    }

    @Override
    public void spawnTo(Player player) {
        if (this.spawned && player.spawned && this.isAlive() && player.isAlive() && player.getLevel() == this.level && player.canSee(this) && !this.isSpectator()) {
            super.spawnTo(player);
        }
    }

    public boolean getRemoveFormat() {
        return removeFormat;
    }

    public void setRemoveFormat() {
        this.setRemoveFormat(true);
    }

    public void setRemoveFormat(boolean remove) {
        this.removeFormat = remove;
    }

    public boolean canSee(Player player) {
        return !this.hiddenPlayers.containsKey(player.getUniqueId());
    }

    public void hidePlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.put(player.getUniqueId(), player);
        player.despawnFrom(this);
    }

    public void showPlayer(Player player) {
        if (this == player) {
            return;
        }
        this.hiddenPlayers.remove(player.getUniqueId());
        if (player.isOnline()) {
            player.spawnTo(this);
        }
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return false;
    }

    public boolean canPickupXP() {
        return this.canPickupXP;
    }

    public void setCanPickupXP(boolean canPickupXP) {
        this.canPickupXP = canPickupXP;
    }

    @Override
    public void resetFallDistance() {
        super.resetFallDistance();
        if (this.inAirTicks != 0) {
            this.startAirTicks = 10;
        }
        this.inAirTicks = 0;
        this.highestPosition = this.y;
    }

    @Override
    public boolean isOnline() {
        return this.connected && this.loggedIn;
    }

    @Override
    public boolean isOp() {
        return this.server.isOp(this.username);
    }

    @Override
    public void setOp(boolean value) {
        if (value == this.isOp()) {
            return;
        }

        if (value) {
            this.server.addOp(this.username);
        } else {
            this.server.removeOp(this.username);
        }

        this.recalculatePermissions();
        this.adventureSettings.update();
        this.sendCommandData();
    }

    @Override
    public boolean isPermissionSet(String name) {
        return this.perm.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(Permission permission) {
        return this.perm.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(String name) {
        return this.perm != null && this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission permission) {
        return this.perm.hasPermission(permission);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        return this.addAttachment(plugin, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name) {
        return this.addAttachment(plugin, name, null);
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, Boolean value) {
        return this.perm.addAttachment(plugin, name, value);
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        this.perm.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
        this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);

        if (this.perm == null) {
            return;
        }

        this.perm.recalculatePermissions();

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }

        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        if (this.enableClientCommand && spawned) this.sendCommandData();
    }

    public boolean isEnableClientCommand() {
        return this.enableClientCommand;
    }

    public void setEnableClientCommand(boolean enable) {
        this.enableClientCommand = enable;
        SetCommandsEnabledPacket pk = new SetCommandsEnabledPacket();
        pk.enabled = enable;
        this.dataPacket(pk);
        if (enable) this.sendCommandData();
    }

    public void sendCommandData() {
        AvailableCommandsPacket pk = new AvailableCommandsPacket();
        Map<String, CommandDataVersions> data = new HashMap<>();

        for (Command command : this.server.getCommandMap().getCommands().values()) {
            if (!command.testPermissionSilent(this)) {
                continue;
            }

            data.put(command.getName(), command.generateCustomCommandData(this));
        }

        if (data.size() != 0) {
            pk.commands = data;
            this.dataPacket(pk);
        }
    }

    @Override
    public Map<String, PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    public Player(SourceInterface interfaz, Long clientID, InetSocketAddress socketAddress) {
        super(null, new CompoundTag());
        this.interfaz = interfaz;
        this.perm = new PermissibleBase(this);
        this.server = Server.getInstance();
        this.socketAddress = socketAddress;
        this.loaderId = Level.generateChunkLoaderId(this);
        this.gamemode = this.server.getGamemode();
        this.setLevel(this.server.getDefaultLevel());
        this.viewDistance = this.server.getViewDistance();
        this.chunkRadius = viewDistance;
        this.boundingBox = new AxisAlignedBB(0, 0, 0, 0, 0, 0);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.addDefaultWindows();
    }

    public boolean isPlayer() {
        return true;
    }

    public void removeAchievement(String achievementId) {
        achievements.remove(achievementId);
    }

    public boolean hasAchievement(String achievementId) {
        return achievements.contains(achievementId);
    }

    public boolean isConnected() {
        return connected;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.displayName, this.getSkin(), this.loginChainData.getXUID());
        }
    }

    @Override
    public void setSkin(Skin skin) {
        super.setSkin(skin);
        if (this.spawned) {
            this.server.updatePlayerListData(this.getUniqueId(), this.getId(), this.displayName, skin, this.loginChainData.getXUID());
        }
    }

    public String getAddress() {
        return this.socketAddress.getAddress().getHostAddress();
    }

    public int getPort() {
        return this.socketAddress.getPort();
    }

    public InetSocketAddress getSocketAddress() {
        return this.socketAddress;
    }

    public Position getNextPosition() {
        return this.newPosition != null ? new Position(this.newPosition.x, this.newPosition.y, this.newPosition.z, this.level) : this.getPosition();
    }

    public boolean isSleeping() {
        return this.sleeping != null;
    }

    public int getInAirTicks() {
        return this.inAirTicks;
    }

    /**
     * Returns whether the player is currently using an item (right-click and hold).
     *
     * @return bool
     */
    public boolean isUsingItem() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_ACTION) && this.startAction > -1;
    }

    public void setUsingItem(boolean value) {
        this.startAction = value ? this.server.getTick() : -1;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, value);
    }

    public String getButtonText() {
        return this.buttonText;
    }

    public void setButtonText(String text) {
        if (!text.equals(buttonText)) {
            this.buttonText = text;
            this.setDataPropertyAndSendOnlyToSelf(new StringEntityData(Entity.DATA_INTERACTIVE_TAG, this.buttonText));
        }
    }

    public void unloadChunk(int x, int z) {
        this.unloadChunk(x, z, null);
    }

    public void unloadChunk(int x, int z, Level level) {
        level = level == null ? this.level : level;
        long index = Level.chunkHash(x, z);
        if (this.usedChunks.containsKey(index)) {
            for (Entity entity : level.getChunkEntities(x, z).values()) {
                if (entity != this) {
                    entity.despawnFrom(this);
                }
            }

            this.usedChunks.remove(index);
        }
        level.unregisterChunkLoader(this, x, z);
        this.loadQueue.remove(index);
    }

    public Position getSpawn() {
        if (this.spawnPosition != null && this.spawnPosition.getLevel() != null) {
            return this.spawnPosition;
        } else {
            return this.server.getDefaultLevel().getSafeSpawn();
        }
    }

    public void sendChunk(int x, int z, DataPacket packet) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), Boolean.TRUE);

        this.dataPacket(packet);

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }
    }

    public void sendChunk(int x, int z, int subChunkCount, byte[] payload) {
        if (!this.connected) {
            return;
        }

        this.usedChunks.put(Level.chunkHash(x, z), true);

        LevelChunkPacket pk = new LevelChunkPacket();
        pk.chunkX = x;
        pk.chunkZ = z;
        pk.subChunkCount = subChunkCount;
        pk.data = payload;
        //pk.setChannel(Network.CHANNEL_WORLD_CHUNKS);

        //this.batchDataPacket(pk);
        this.server.batchPackets(new Player[]{this}, new DataPacket[]{pk}, true);

        if (this.spawned) {
            for (Entity entity : this.level.getChunkEntities(x, z).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }
    }

    protected void sendNextChunk() {
        if (!this.connected) {
            return;
        }

        if (Timings.playerChunkSendTimer != null) Timings.playerChunkSendTimer.startTiming();

        if (!loadQueue.isEmpty()) {
            int count = 0;
            ObjectIterator<Long2ObjectMap.Entry<Boolean>> iter = loadQueue.long2ObjectEntrySet().fastIterator();
            while (iter.hasNext()) {
                if (count >= server.chunksPerTick) {
                    break;
                }

                Long2ObjectMap.Entry<Boolean> entry = iter.next();
                long index = entry.getLongKey();
                int chunkX = Level.getHashX(index);
                int chunkZ = Level.getHashZ(index);

                ++count;

                this.usedChunks.put(index, false);
                this.level.registerChunkLoader(this, chunkX, chunkZ, false);

                if (!this.level.populateChunk(chunkX, chunkZ)) {
                    if (this.spawned && this.teleportPosition == null) {
                        continue;
                    } else {
                        break;
                    }
                }

                iter.remove();

                PlayerChunkRequestEvent ev = new PlayerChunkRequestEvent(this, chunkX, chunkZ);
                this.server.getPluginManager().callEvent(ev);
                if (!ev.isCancelled()) {
                    this.level.requestChunk(chunkX, chunkZ, this);
                }
            }
        }

        if (this.spawnChunkLoadCount != -1 && ++this.spawnChunkLoadCount >= server.spawnThreshold) {
            if (this.protocol < 274) {
                this.locallyInitialized = true;
                this.doFirstSpawn();
            }

            this.sendPlayStatus(PlayStatusPacket.PLAYER_SPAWN);
            this.spawnChunkLoadCount = -1;

            // Not really needed on Nukkit PM1E but it's here for plugin compatibility
            this.server.getPluginManager().callEvent(new PlayerLocallyInitializedEvent(this));
        }

        if (Timings.playerChunkSendTimer != null) Timings.playerChunkSendTimer.stopTiming();
    }

    protected void doFirstSpawn() {
        if (this.spawned) {
            return;
        }

        this.noDamageTicks = 60;
        this.setAirTicks(400);
        this.sendAttributes();

        if (this.hasPermission(Server.BROADCAST_CHANNEL_USERS)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_USERS, this);
        }
        if (this.hasPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE)) {
            this.server.getPluginManager().subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this);
        }

        Position pos = this.level.getSafeSpawn(this);

        PlayerRespawnEvent respawnEvent = new PlayerRespawnEvent(this, pos, true);

        this.server.getPluginManager().callEvent(respawnEvent);

        if (this.getHealth() < 1 || this.protocol < 274) {
            pos = this.getSpawn();
            RespawnPacket respawnPacket = new RespawnPacket();
            respawnPacket.x = (float) pos.x;
            respawnPacket.y = (float) pos.y;
            respawnPacket.z = (float) pos.z;
            this.dataPacket(respawnPacket);
        }

        this.getLevel().sendTime(this);
        this.getLevel().sendWeather(this);

        this.setImmobile(false);
        this.spawned = true;

        PlayerJoinEvent playerJoinEvent = new PlayerJoinEvent(this,
                new TranslationContainer(TextFormat.YELLOW + "%multiplayer.player.joined", new String[]{this.displayName})
        );

        this.server.getPluginManager().callEvent(playerJoinEvent);

        if (playerJoinEvent.getJoinMessage().toString().trim().length() > 0) {
            this.server.broadcastMessage(playerJoinEvent.getJoinMessage());
        }

        for (long index : this.usedChunks.keySet()) {
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);
            for (Entity entity : this.level.getChunkEntities(chunkX, chunkZ).values()) {
                if (this != entity && !entity.closed && entity.isAlive()) {
                    entity.spawnTo(this);
                }
            }
        }

        // Prevent PlayerTeleportEvent during player spawn
        //this.teleport(pos, null);

        if (!this.isSpectator()) {
            this.spawnToAll();
        }
    }

    protected boolean orderChunks() {
        if (!this.connected) {
            return false;
        }

        if (Timings.playerChunkOrderTimer != null) Timings.playerChunkOrderTimer.startTiming();

        this.nextChunkOrderRun = 200;

        loadQueue.clear();
        Long2ObjectOpenHashMap<Boolean> lastChunk = new Long2ObjectOpenHashMap<>(this.usedChunks);

        int centerX = (int) this.x >> 4;
        int centerZ = (int) this.z >> 4;

        int radius = spawned ? this.chunkRadius : server.c_s_spawnThreshold;
        int radiusSqr = radius * radius;

        long index;
        for (int x = 0; x <= radius; x++) {
            int xx = x * x;
            for (int z = 0; z <= x; z++) {
                int distanceSqr = xx + z * z;
                if (distanceSqr > radiusSqr) continue;

                /* Top right quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX + x, centerZ + z)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                /* Top left quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX - x - 1, centerZ + z)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                /* Bottom right quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX + x, centerZ - z - 1)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                /* Bottom left quadrant */
                if (this.usedChunks.get(index = Level.chunkHash(centerX - x - 1, centerZ - z - 1)) != Boolean.TRUE) {
                    this.loadQueue.put(index, Boolean.TRUE);
                }
                lastChunk.remove(index);
                if (x != z) {
                    /* Top right quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX + z, centerZ + x)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                    /* Top left quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX - z - 1, centerZ + x)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                    /* Bottom right quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX + z, centerZ - x - 1)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                    /* Bottom left quadrant mirror */
                    if (this.usedChunks.get(index = Level.chunkHash(centerX - z - 1, centerZ - x - 1)) != Boolean.TRUE) {
                        this.loadQueue.put(index, Boolean.TRUE);
                    }
                    lastChunk.remove(index);
                }
            }
        }

        LongIterator keys = lastChunk.keySet().iterator();
        while (keys.hasNext()) {
            index = keys.nextLong();
            this.unloadChunk(Level.getHashX(index), Level.getHashZ(index));
        }

        if (this.protocol >= 313) {
            if (!loadQueue.isEmpty()) {
                NetworkChunkPublisherUpdatePacket packet = new NetworkChunkPublisherUpdatePacket();
                packet.position = this.asBlockVector3();
                packet.radius = viewDistance << 4;
                this.dataPacket(packet);
            }
        }

        if (Timings.playerChunkOrderTimer != null) Timings.playerChunkOrderTimer.stopTiming();
        return true;
    }

    public boolean batchDataPacket(DataPacket packet) {
        if (!this.connected) {
            return false;
        }

        packet.protocol = this.protocol;

        try (Timing ignore = Timings.getSendDataPacketTiming(packet)) {
            if (server.callDataPkEv) {
                DataPacketSendEvent event = new DataPacketSendEvent(this, packet);
                this.server.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return false;
                }
            }

            /*if (!this.batchedPackets.containsKey(packet.getChannel())) {
                this.batchedPackets.put(packet.getChannel(), new ArrayList<>());
            }

            this.batchedPackets.get(packet.getChannel()).add(packet.clone());*/
            batchedPackets.add(packet.clone());
        }
        return true;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     * @param packet packet to send
     * @return packet successfully sent
     */
    public boolean dataPacket(DataPacket packet) {
        if (!this.connected) {
            return false;
        }

        packet.protocol = this.protocol;

        if (packet instanceof StartGamePacket) {
            ((StartGamePacket) packet).version = this.version;
        }

        try (Timing ignore = Timings.getSendDataPacketTiming(packet)) {
            if (server.callDataPkEv) {
                DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
                this.server.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return false;
                }
            }

            if (Nukkit.DEBUG > 2 /*&& !server.isIgnoredPacket(packet.getClass())*/) {
                log.trace("Outbound {}: {}", this.getName(), packet);
            }

            this.interfaz.putPacket(this, packet, false, false);
        }

        return true;
    }

    public int dataPacket(DataPacket packet, boolean needACK) {
        return this.dataPacket(packet) ? 0 : -1;
    }

    /**
     * 0 is true
     * -1 is false
     * other is identifer
     * @param packet packet to send
     * @return packet successfully sent
     */
    public boolean directDataPacket(DataPacket packet) {
        if (!this.connected) {
            return false;
        }

        packet.protocol = this.protocol;

        try (Timing ignore = Timings.getSendDataPacketTiming(packet)) {
            if (server.callDataPkEv) {
                DataPacketSendEvent ev = new DataPacketSendEvent(this, packet);
                this.server.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return false;
                }
            }

            this.interfaz.putPacket(this, packet, false, true);
        }

        return true;
    }

    public int directDataPacket(DataPacket packet, boolean needACK) {
        return this.directDataPacket(packet) ? 0 : -1;
    }

    public int getPing() {
        return this.interfaz.getNetworkLatency(this);
    }

    public boolean sleepOn(Vector3 pos) {
        if (!this.isOnline()) {
            return false;
        }

        Entity[] e = this.level.getNearbyEntities(this.boundingBox.grow(2, 1, 2), this);
        for (Entity p : e) {
            if (p instanceof Player) {
                if (((Player) p).sleeping != null && pos.distance(((Player) p).sleeping) <= 0.1) {
                    return false;
                }
            }
        }

        PlayerBedEnterEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerBedEnterEvent(this, this.level.getBlock(pos)));
        if (ev.isCancelled()) {
            return false;
        }

        this.sleeping = pos.clone();
        this.teleport(new Location(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, this.yaw, this.pitch, this.level), null);

        this.setDataProperty(new IntPositionEntityData(DATA_PLAYER_BED_POSITION, (int) pos.x, (int) pos.y, (int) pos.z));
        this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, true);

        if (this.getServer().bedSpawnpoints) {
            if (!this.getSpawn().equals(pos)) {
                this.setSpawn(pos);
                this.sendTranslation("§7%tile.bed.respawnSet");
            }
        }

        this.level.sleepTicks = 60;
        this.ticksSinceLastRest = 0;

        return true;
    }

    public void setSpawn(Vector3 pos) {
        Level level;
        if (!(pos instanceof Position)) {
            level = this.level;
        } else {
            level = ((Position) pos).getLevel();
        }
        this.spawnPosition = new Position(pos.x, pos.y, pos.z, level);
        SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
        pk.spawnType = SetSpawnPositionPacket.TYPE_PLAYER_SPAWN;
        pk.x = (int) this.spawnPosition.x;
        pk.y = (int) this.spawnPosition.y;
        pk.z = (int) this.spawnPosition.z;
        this.dataPacket(pk);
    }

    public void stopSleep() {
        if (this.sleeping != null) {
            this.server.getPluginManager().callEvent(new PlayerBedLeaveEvent(this, this.level.getBlock(this.sleeping)));

            this.sleeping = null;
            this.setDataProperty(new IntPositionEntityData(DATA_PLAYER_BED_POSITION, 0, 0, 0));
            this.setDataFlag(DATA_PLAYER_FLAGS, DATA_PLAYER_FLAG_SLEEP, false);

            this.level.sleepTicks = 0;

            AnimatePacket pk = new AnimatePacket();
            pk.eid = this.id;
            pk.action = AnimatePacket.Action.WAKE_UP;
            this.dataPacket(pk);
        }
    }

    public Vector3 getSleepingPos() {
        return this.sleeping;
    }

    public boolean awardAchievement(String achievementId) {
        if (!Server.getInstance().achievements) {
            return false;
        }

        Achievement achievement = Achievement.achievements.get(achievementId);

        if (achievement == null || hasAchievement(achievementId)) {
            return false;
        }

        for (String id : achievement.requires) {
            if (!this.hasAchievement(id)) {
                return false;
            }
        }
        PlayerAchievementAwardedEvent event = new PlayerAchievementAwardedEvent(this, achievementId);
        this.server.getPluginManager().callEvent(event);

        if (event.isCancelled()) {
            return false;
        }

        this.achievements.add(achievementId);
        achievement.broadcast(this);
        return true;
    }

    public int getGamemode() {
        return gamemode;
    }

    /**
     * Returns a client-friendly gamemode of the specified real gamemode
     * This function takes care of handling gamemodes known to MCPE (as of 1.1.0.3, that includes Survival, Creative and Adventure)
     */
    private static int getClientFriendlyGamemode(int gamemode) {
        gamemode &= 0x03;
        if (gamemode == Player.SPECTATOR) {
            return Player.CREATIVE;
        }
        return gamemode;
    }

    public boolean setGamemode(int gamemode) {
        return this.setGamemode(gamemode, false, null);
    }

    public boolean setGamemode(int gamemode, boolean clientSide) {
        return this.setGamemode(gamemode, clientSide, null);
    }

    public boolean setGamemode(int gamemode, boolean clientSide, AdventureSettings newSettings) {
        if (gamemode < 0 || gamemode > 3 || this.gamemode == gamemode) {
            return false;
        }

        if (newSettings == null) {
            newSettings = this.adventureSettings.clone(this);
            newSettings.set(Type.WORLD_IMMUTABLE, (gamemode & 0x02) > 0);
            newSettings.set(Type.BUILD_AND_MINE, (gamemode & 0x02) <= 0);
            newSettings.set(Type.WORLD_BUILDER, (gamemode & 0x02) <= 0);
            newSettings.set(Type.ALLOW_FLIGHT, (gamemode & 0x01) > 0);
            newSettings.set(Type.NO_CLIP, gamemode == 0x03);
            newSettings.set(Type.FLYING, gamemode == 0x03);
        }

        PlayerGameModeChangeEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerGameModeChangeEvent(this, gamemode, newSettings));

        if (ev.isCancelled()) {
            return false;
        }

        this.gamemode = gamemode;

        if (this.isSpectator()) {
            this.keepMovement = true;
            this.despawnFromAll();
        } else {
            this.keepMovement = false;
            this.spawnToAll();
        }

        this.namedTag.putInt("playerGameType", this.gamemode);

        if (!clientSide) {
            SetPlayerGameTypePacket pk = new SetPlayerGameTypePacket();
            pk.gamemode = getClientFriendlyGamemode(gamemode);
            this.dataPacket(pk);
        }

        this.setAdventureSettings(ev.getNewAdventureSettings());

        if (this.isSpectator()) {
            this.adventureSettings.set(Type.FLYING, true);
            this.teleport(this.temporalVector.setComponents(this.x, this.y + 0.1, this.z));

            if (this.protocol < 407) {
                InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
                inventoryContentPacket.inventoryId = InventoryContentPacket.SPECIAL_CREATIVE;
                this.dataPacket(inventoryContentPacket);
            }
        } else {
            if (this.isSurvival() || this.isAdventure()) {
                this.adventureSettings.set(Type.FLYING, false);
            }
            if (this.protocol < 407) {
                InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
                inventoryContentPacket.inventoryId = InventoryContentPacket.SPECIAL_CREATIVE;
                inventoryContentPacket.slots = Item.getCreativeItems(this.protocol).toArray(new Item[0]);
                this.dataPacket(inventoryContentPacket);
            }
        }

        this.resetFallDistance();

        this.inventory.sendContents(this);
        this.inventory.sendContents(this.getViewers().values());
        this.inventory.sendHeldItem(this.hasSpawned.values());
        this.offhandInventory.sendContents(this);
        this.offhandInventory.sendContents(this.getViewers().values());

        this.inventory.sendCreativeContents();
        return true;
    }

    public void sendSettings() {
        this.adventureSettings.update();
    }

    public boolean isSurvival() {
        return this.gamemode == SURVIVAL;
    }

    public boolean isCreative() {
        return this.gamemode == CREATIVE;
    }

    public boolean isSpectator() {
        return this.gamemode == SPECTATOR;
    }

    public boolean isAdventure() {
        return this.gamemode == ADVENTURE;
    }

    @Override
    public Item[] getDrops() {
        if (!this.isCreative() && !this.isSpectator()) {
            if (this.inventory != null) {
                List<Item> drops = new ArrayList<>(this.inventory.getContents().values());
                drops.addAll(this.offhandInventory.getContents().values());
                drops.addAll(this.playerUIInventory.getContents().values());
                return drops.toArray(new Item[0]);
            }
            return new Item[0];
        }

        return new Item[0];
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean setDataProperty(EntityData data) {
        return setDataProperty(data, true);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean setDataProperty(EntityData data, boolean send) {
        if (super.setDataProperty(data, send)) {
            if (send) this.sendData(this, new EntityMetadata().put(this.getDataProperty(data.getId())));
            return true;
        }
        return false;
    }

    @Override
    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        if (!this.onGround || movX != 0 || movY != 0 || movZ != 0) {
            boolean onGround = false;

            AxisAlignedBB bb = this.boundingBox.clone();
            bb.maxY = bb.minY + 0.5;
            bb.minY -= 1;

            AxisAlignedBB realBB = this.boundingBox.clone();
            realBB.maxY = realBB.minY + 0.1;
            realBB.minY -= 0.2;

            int minX = NukkitMath.floorDouble(bb.minX);
            int minY = NukkitMath.floorDouble(bb.minY);
            int minZ = NukkitMath.floorDouble(bb.minZ);
            int maxX = NukkitMath.ceilDouble(bb.maxX);
            int maxY = NukkitMath.ceilDouble(bb.maxY);
            int maxZ = NukkitMath.ceilDouble(bb.maxZ);

            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        Block block = this.level.getBlock(this.temporalVector.setComponents(x, y, z), false);

                        if (!block.canPassThrough() && block.collidesWithBB(realBB)) {
                            onGround = true;
                            break;
                        }
                    }
                }
            }

            this.onGround = onGround;
        }

        this.isCollided = this.onGround;
    }

    @Override
    protected void checkBlockCollision() {
        boolean portal = false;
        boolean endPortal = false;

        for (Block block : this.getCollisionBlocks()) {
            if (block.getId() == Block.NETHER_PORTAL) {
                portal = true;
                continue;
            } else if (block.getId() == Block.END_PORTAL) {
                endPortal = true;
                continue;
            }

            block.onEntityCollide(this);
        }

        if (endPortal) {
            inEndPortalTicks++;
        } else {
            this.inEndPortalTicks = 0;
        }

        if (server.endEnabled && inEndPortalTicks == 1) {
            EntityPortalEnterEvent ev = new EntityPortalEnterEvent(this, EntityPortalEnterEvent.PortalType.END);
            this.getServer().getPluginManager().callEvent(ev);

            if (!ev.isCancelled()) {
                if (this.getLevel().isEnd) {
                    if (server.vanillaPortals && this.getSpawn().getLevel().getDimension() == Level.DIMENSION_OVERWORLD) {
                        this.teleport(this.getSpawn(), TeleportCause.END_PORTAL);
                    } else {
                        this.teleport(this.getServer().getDefaultLevel().getSafeSpawn(), TeleportCause.END_PORTAL);
                    }
                } else {
                    Level end = this.getServer().getLevelByName("the_end");
                    if (end != null) {
                        this.teleport(end.getSafeSpawn(), TeleportCause.END_PORTAL);
                    }
                }
            }
        }

        if (portal) {
            this.inPortalTicks++;
        } else {
            this.inPortalTicks = 0;
            this.portalPos = null;
        }

        if (this.server.isNetherAllowed()) {
            if (this.server.vanillaPortals && (this.inPortalTicks == 40 || this.inPortalTicks == 10 && this.gamemode == CREATIVE) && this.portalPos == null) {
                Position portalPos = this.level.calculatePortalMirror(this);
                if (portalPos == null) {
                    return;
                }

                for (int x = -1; x < 2; x++) {
                    for (int z = -1; z < 2; z++) {
                        int chunkX = (portalPos.getFloorX() >> 4) + x, chunkZ = (portalPos.getFloorZ() >> 4) + z;
                        FullChunk chunk = portalPos.level.getChunk(chunkX, chunkZ, false);
                        if (chunk == null || !(chunk.isGenerated() || chunk.isPopulated())) {
                            portalPos.level.generateChunk(chunkX, chunkZ, true);
                        }
                    }
                }
                this.portalPos = portalPos;
            }

            if (this.inPortalTicks == 80 || (this.server.vanillaPortals && this.inPortalTicks == 25 && this.gamemode == CREATIVE)) {
                EntityPortalEnterEvent ev = new EntityPortalEnterEvent(this, EntityPortalEnterEvent.PortalType.NETHER);
                this.getServer().getPluginManager().callEvent(ev);

                if (ev.isCancelled()) {
                    this.portalPos = null;
                    return;
                }

                if (server.vanillaPortals) {
                    Position foundPortal = BlockNetherPortal.findNearestPortal(this.portalPos);
                    if (foundPortal == null) {
                        BlockNetherPortal.spawnPortal(this.portalPos);
                        this.teleport(this.portalPos.add(1.5, 1, 0.5));
                    } else {
                        this.teleport(BlockNetherPortal.getSafePortal(foundPortal));
                    }
                    this.portalPos = null;
                } else {
                    if (this.getLevel().isNether) {
                        this.teleport(this.getServer().getDefaultLevel().getSafeSpawn(), TeleportCause.NETHER_PORTAL);
                    } else {
                        Level nether = this.getServer().getLevelByName("nether");
                        if (nether != null) {
                            this.teleport(nether.getSafeSpawn(), TeleportCause.NETHER_PORTAL);
                        }
                    }
                }
            }
        }
    }

    protected void checkNearEntities() {
        Entity[] e = this.level.getNearbyEntities(this.boundingBox.grow(1, 0.5, 1), this);
        for (Entity entity : e) {
            //entity.scheduleUpdate();

            if (!entity.isAlive() || !this.isAlive()) {
                continue;
            }

            this.pickupEntity(entity, true);
        }
    }

    protected void processMovement(int tickDiff) {
        if (!this.isAlive() || !this.spawned || this.newPosition == null || this.teleportPosition != null || this.isSleeping()) {
            return;
        }
        Vector3 newPos = this.newPosition;
        double distanceSquared = newPos.distanceSquared(this);
        boolean revert = false;
        if ((distanceSquared / ((double) (tickDiff * tickDiff))) > 100 && (newPos.y - this.y) > -5) {
            revert = true;
        } else if (this.chunk == null || !this.chunk.isGenerated()) {
            BaseFullChunk chunk = this.level.getChunk((int) newPos.x >> 4, (int) newPos.z >> 4, false);
            if (chunk == null || !chunk.isGenerated()) {
                revert = true;
                this.nextChunkOrderRun = 0;
            } else {
                if (this.chunk != null) {
                    this.chunk.removeEntity(this);
                }
                this.chunk = chunk;
            }
        }

        double tdx = newPos.x - this.x;
        double tdz = newPos.z - this.z;
        double distance = Math.sqrt(tdx * tdx + tdz * tdz);

        if (!revert && distanceSquared != 0) {
            double dx = newPos.x - this.x;
            double dy = newPos.y - this.y;
            double dz = newPos.z - this.z;

            //the client likes to clip into blocks like stairs, but we do full server-side prediction of that without
            //help from the client's position changes, so we deduct the expected clip height from the moved distance.
            double expectedClipDistance = this.ySize * (1 - STEP_CLIP_MULTIPLIER);
            dy -= expectedClipDistance;

            this.fastMove(dx, dy, dz);
            if (this.newPosition == null) {
                return;
            }

            double diffX = this.x - newPos.x;
            double diffY = this.y - newPos.y;
            double diffZ = this.z - newPos.z;

            double yS = 0.5 + this.ySize;
            if (diffY >= -yS || diffY <= yS) {
                diffY = 0;
            }

            if (diffX != 0 || diffY != 0 || diffZ != 0) {
                if (this.checkMovement && !server.getAllowFlight() && (this.isSurvival() || this.isAdventure())) {
                    if (!this.isSleeping() && this.riding == null && !this.hasEffect(Effect.LEVITATION)) {
                        if ((diffX * diffX + diffZ * diffZ) / ((double) (tickDiff * tickDiff)) > 0.5 ) {
                            PlayerInvalidMoveEvent ev;
                            this.getServer().getPluginManager().callEvent(ev = new PlayerInvalidMoveEvent(this, true));
                            if (!ev.isCancelled()) {
                                revert = ev.isRevert();
                            }
                        }
                    }
                }

                if (!revert) {
                    this.x = newPos.x;
                    this.y = newPos.y;
                    this.z = newPos.z;
                    double radius = this.getWidth() / 2;
                    this.boundingBox.setBounds(this.x - radius, this.y, this.z - radius, this.x + radius, this.y + this.getHeight(), this.z + radius);
                }
            }
        }

        Location from = new Location(
                this.lastX,
                this.lastY,
                this.lastZ,
                this.lastYaw,
                this.lastPitch,
                this.level);
        Location to = this.getLocation();

        double delta = Math.pow(this.lastX - to.x, 2) + Math.pow(this.lastY - to.y, 2) + Math.pow(this.z - to.z, 2);
        double deltaAngle = Math.abs(this.lastYaw - to.yaw) + Math.abs(this.lastPitch - to.pitch);

        if (!revert && (delta > 0.0001d || deltaAngle > 1d)) {
            boolean isFirst = this.firstMove;

            this.firstMove = false;
            this.lastX = to.x;
            this.lastY = to.y;
            this.lastZ = to.z;

            this.lastYaw = to.yaw;
            this.lastPitch = to.pitch;

            if (!isFirst) {
                List<Block> blocksAround = new ArrayList<>(this.blocksAround);
                List<Block> collidingBlocks = new ArrayList<>(this.collisionBlocks);

                PlayerMoveEvent ev = new PlayerMoveEvent(this, from, to);

                this.blocksAround = null;
                this.collisionBlocks = null;

                this.server.getPluginManager().callEvent(ev);

                if (!(revert = ev.isCancelled())) {
                    if (this.server.getMobAiEnabled() && this.level.getCurrentTick() % 20 == 0) {
                        AxisAlignedBB aab = new AxisAlignedBB(
                                this.getX() - 0.6f,
                                this.getY() + 1.45f,
                                this.getZ() - 0.6f,
                                this.getX() + 0.6f,
                                this.getY() + 2.9f,
                                this.getZ() + 0.6f
                        );
                        for (int i = 0; i < 8; i++) {
                            Entity[] entities = this.level.getCollidingEntities(aab.offset(-Math.sin(this.getYaw() * Math.PI / 180) * i, i * (Math.tan(this.getPitch() * -1 * Math.PI / 180)), Math.cos(this.getYaw() * Math.PI / 180) * i));
                            if (entities.length > 0) {
                                for (Entity e : entities) {
                                    if (e instanceof EntityEnderman) {
                                        ((EntityEnderman) e).stareToAngry();
                                    }
                                }
                            }
                        }
                    }

                    if (!to.equals(ev.getTo())) {
                        this.teleport(ev.getTo(), null);
                    } else {
                        this.addMovement(this.x, this.y + this.getEyeHeight(), this.z, this.yaw, this.pitch, this.yaw);
                    }
                } else {
                    this.blocksAround = blocksAround;
                    this.collisionBlocks = collidingBlocks;
                }
            }

            if (this.speed == null) speed = new Vector3(from.x - to.x, from.y - to.y, from.z - to.z);
            else this.speed.setComponents(from.x - to.x, from.y - to.y, from.z - to.z);
        } else {
            if (this.speed == null) speed = new Vector3(0, 0, 0);
            else this.speed.setComponents(0, 0, 0);
        }

        if (!revert && (this.isFoodEnabled() || this.getServer().getDifficulty() == 0)) {
            if ((this.isSurvival() || this.isAdventure())) {

                if (distance >= 0.05) {
                    double jump = 0;
                    double swimming = this.isInsideOfWater() ? 0.015 * distance : 0;
                    if (swimming != 0) distance = 0;
                    if (this.isSprinting()) {
                        if (this.inAirTicks == 3 && swimming == 0) {
                            jump = 0.7;
                        }
                        this.foodData.updateFoodExpLevel(0.06 * distance + jump + swimming);
                    } else {
                        if (this.inAirTicks == 3 && swimming == 0) {
                            jump = 0.2;
                        }
                        this.foodData.updateFoodExpLevel(0.01 * distance + jump + swimming);
                    }
                }
            }
        }

        // Frost Walker
        if (!revert && delta > 0.0001d) {
            Enchantment frostWalker = inventory.getBootsFast().getEnchantment(Enchantment.ID_FROST_WALKER);
            if (frostWalker != null && frostWalker.getLevel() > 0 && !this.isSpectator() && this.y >= 1 && this.y <= 255) {
                int radius = 2 + frostWalker.getLevel();
                for (int coordX = this.getFloorX() - radius; coordX < this.getFloorX() + radius + 1; coordX++) {
                    for (int coordZ = this.getFloorZ() - radius; coordZ < this.getFloorZ() + radius + 1; coordZ++) {
                        Block block = level.getBlock(coordX, this.getFloorY() - 1, coordZ);
                        if ((block.getId() == Block.STILL_WATER || block.getId() == Block.WATER && block.getDamage() == 0) && block.up().getId() == Block.AIR) {
                            WaterFrostEvent ev = new WaterFrostEvent(block);
                            server.getPluginManager().callEvent(ev);
                            if (!ev.isCancelled()) {
                                level.setBlock(block, Block.get(Block.ICE_FROSTED), true, false);
                                level.scheduleUpdate(level.getBlock(block), Utils.random.nextInt(20, 40));
                            }
                        }
                    }
                }
            }
        }

        if (revert) {
            this.lastX = from.x;
            this.lastY = from.y;
            this.lastZ = from.z;

            this.lastYaw = from.yaw;
            this.lastPitch = from.pitch;

            // We have to send slightly above otherwise the player will fall into the ground
            Vector3 vec = new Vector3(from.x, from.y + 0.00001, from.z);
            this.sendPosition(vec, from.yaw, from.pitch, MovePlayerPacket.MODE_RESET);
            this.forceMovement = vec;
        } else {
            this.forceMovement = null;
            if (distanceSquared != 0 && this.nextChunkOrderRun > 20) {
                this.nextChunkOrderRun = 20;
            }
        }

        this.newPosition = null;
    }

    @Override
    public void addMovement(double x, double y, double z, double yaw, double pitch, double headYaw) {
        this.sendPosition(x, y, z, yaw, pitch, MovePlayerPacket.MODE_NORMAL, this.getViewers().values());
    }

    @Override
    public boolean setMotion(Vector3 motion) {
        if (super.setMotion(motion)) {
            if (this.chunk != null) {
                this.addMotion(this.motionX, this.motionY, this.motionZ); // Send to others
                SetEntityMotionPacket pk = new SetEntityMotionPacket();
                pk.eid = this.id;
                pk.motionX = (float) motion.x;
                pk.motionY = (float) motion.y;
                pk.motionZ = (float) motion.z;
                //pk.setChannel(Network.CHANNEL_MOVEMENT);
                this.dataPacket(pk);
            }

            if (this.motionY > 0) {
                this.startAirTicks = (int) ((-(Math.log(this.getGravity() / (this.getGravity() + this.getDrag() * this.motionY))) / this.getDrag()) * 2 + 5);
            }

            return true;
        }

        return false;
    }

    public void sendAttributes() {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entityId = this.getId();
        pk.entries = new Attribute[]{
                Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0),
                Attribute.getAttribute(Attribute.MAX_HUNGER).setValue(this.foodData.getLevel()),
                Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(this.getMovementSpeed()),
                Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(this.expLevel),
                Attribute.getAttribute(Attribute.EXPERIENCE).setValue(((float) this.exp) / calculateRequireExperience(this.expLevel))
        };
        this.dataPacket(pk);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.loggedIn) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;

        if (tickDiff <= 0) {
            return true;
        }

        this.lastUpdate = currentTick;

        this.failedTransactions = 0;

        if (this.fishing != null && this.server.getTick() % 20 == 0) {
            if (this.distanceSquared(fishing) > 1089) { // 33 blocks
                this.stopFishing(false);
            }
        }

        if (!this.isAlive() && this.spawned) {
            //++this.deadTicks;
            //if (this.deadTicks >= 10) {
            this.despawnFromAll(); // HACK: fix "dead" players
            //}
            return true;
        }

        if (this.spawned) {
            this.processMovement(tickDiff);
            this.motionX = this.motionY = this.motionZ = 0; // HACK: fix player knockback being messed up

            if (currentTick % 2 == 0) {
                if (!this.isSpectator()) {
                    this.checkNearEntities();
                }
            }

            this.entityBaseTick(tickDiff);

            if (this.getServer().getDifficulty() == 0 && this.level.getGameRules().getBoolean(GameRule.NATURAL_REGENERATION)) {
                if (this.getHealth() < this.getMaxHealth() && this.age % 20 == 0) {
                    this.heal(1);
                }

                if (this.foodData.getLevel() < 20 && this.age % 10 == 0) {
                    this.foodData.addFoodLevel(1, 0);
                }
            }

            if (this.isOnFire() && this.lastUpdate % 10 == 0) {
                if (this.isCreative() && !this.isInsideOfFire()) {
                    this.extinguish();
                } else if (this.getLevel().isRaining() && this.getLevel().canBlockSeeSky(this)) {
                    this.extinguish();
                }
            }

            if (!this.isSpectator() && this.speed != null) {
                if (this.onGround) {
                    if (this.inAirTicks != 0) {
                        this.startAirTicks = 10;
                    }
                    this.inAirTicks = 0;
                    this.highestPosition = this.y;
                } else {
                    if (this.checkMovement && !this.isGliding() && !server.getAllowFlight() && !this.adventureSettings.get(Type.ALLOW_FLIGHT) && this.inAirTicks > 20 && !this.isSleeping() && !this.isImmobile() && !this.isSwimming() && this.riding == null && !this.hasEffect(Effect.LEVITATION)) {
                        double expectedVelocity = (-this.getGravity()) / ((double) this.getDrag()) - ((-this.getGravity()) / ((double) this.getDrag())) * Math.exp(-((double) this.getDrag()) * ((double) (this.inAirTicks - this.startAirTicks)));
                        double diff = (this.speed.y - expectedVelocity) * (this.speed.y - expectedVelocity);

                        if (this.isOnLadder()) {
                            this.resetFallDistance();
                        } else {
                            if (diff > 1 && expectedVelocity < this.speed.y && (speed.y < -0.2 || speed.y > 4)) {
                                if (this.inAirTicks < 150) {
                                    PlayerInvalidMoveEvent ev = new PlayerInvalidMoveEvent(this, true);
                                    this.getServer().getPluginManager().callEvent(ev);
                                    if (!ev.isCancelled()) {
                                        this.setMotion(new Vector3(0, expectedVelocity, 0));
                                    }
                                } else if (this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server")) {
                                    return false;
                                }
                            }
                        }
                    }

                    if (this.y > highestPosition) {
                        this.highestPosition = this.y;
                    }

                    if (this.isGliding() || this.isSwimming()) {
                        this.resetFallDistance();
                    } else {
                        ++this.inAirTicks;
                    }
                }

                if (this.isSurvival() || this.isAdventure()) {
                    if (this.foodData != null) this.foodData.update(tickDiff);
                }
            }
        }

        this.checkTeleportPosition();

        /*if (currentTick % 20 == 0) {
            this.checkInteractNearby();
        }*/

        if (this.spawned && !this.dummyBossBars.isEmpty() && currentTick % 100 == 0) {
            this.dummyBossBars.values().forEach(DummyBossBar::updateBossEntityPosition);
        }

        updateBlockingFlag();

        if (!this.isSleeping()) {
            this.ticksSinceLastRest++;
        }

        return true;
    }

    private void updateBlockingFlag() {
        boolean shieldInHand = this.getInventory().getItemInHandFast().getId() == ItemID.SHIELD;
        boolean shieldInOffhand = this.getOffhandInventory().getItemFast(0).getId() == ItemID.SHIELD;
        if (isBlocking()) {
            if (!isSneaking() || (!shieldInHand && !shieldInOffhand)) {
                this.setBlocking(false);
            }
        } else if (isSneaking() && (shieldInHand || shieldInOffhand)) {
            this.setBlocking(true);
        }
    }

    public void checkInteractNearby() {
        int interactDistance = isCreative() ? 5 : 3;
        if (canInteract(this, interactDistance)) {
            EntityInteractable e = getEntityPlayerLookingAt(interactDistance);
            if (e != null) {
                setButtonText(e.getInteractButtonText());
            } else {
                setButtonText("");
            }
        } else {
            setButtonText("");
        }
    }

    /**
     * Returns the Entity the player is looking at currently
     *
     * @param maxDistance the maximum distance to check for entities
     * @return Entity|null    either NULL if no entity is found or an instance of the entity
     */
    public EntityInteractable getEntityPlayerLookingAt(int maxDistance) {
        if (timing != null) timing.startTiming();

        EntityInteractable entity = null;

        if (temporalVector != null) {
            Entity[] nearbyEntities = level.getNearbyEntities(boundingBox.grow(maxDistance, maxDistance, maxDistance), this);

            try {
                BlockIterator itr = new BlockIterator(level, getPosition(), getDirectionVector(), getEyeHeight(), maxDistance);
                if (itr.hasNext()) {
                    Block block;
                    while (itr.hasNext()) {
                        block = itr.next();
                        entity = getEntityAtPosition(nearbyEntities, block.getFloorX(), block.getFloorY(), block.getFloorZ());
                        if (entity != null) {
                            break;
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        if (timing != null) timing.stopTiming();

        return entity;
    }

    private static EntityInteractable getEntityAtPosition(Entity[] nearbyEntities, int x, int y, int z) {
        for (Entity nearestEntity : nearbyEntities) {
            if (nearestEntity.getFloorX() == x && nearestEntity.getFloorY() == y && nearestEntity.getFloorZ() == z
                    && nearestEntity instanceof EntityInteractable
                    && ((EntityInteractable) nearestEntity).canDoInteraction()) {
                return (EntityInteractable) nearestEntity;
            }
        }
        return null;
    }

    public void checkNetwork() {
        if (!this.isOnline()) {
            return;
        }

        if (!this.batchedPackets.isEmpty()) {
            Player[] pArr = new Player[]{this};
            /*for (Entry<Integer, List<DataPacket>> entry : this.batchedPackets.entrySet()) {
                List<DataPacket> packets = entry.getValue();
                DataPacket[] arr = packets.toArray(new DataPacket[0]);
                packets.clear();
                this.server.batchPackets(pArr, arr, false);
            }*/
            this.server.batchPackets(pArr, batchedPackets.toArray(new DataPacket[0]), false);
            this.batchedPackets.clear();
        }

        if (this.nextChunkOrderRun-- <= 0 || this.chunk == null) {
            this.orderChunks();
        }

        if (!this.loadQueue.isEmpty() || !this.spawned) {
            this.sendNextChunk();
        }
    }

    public boolean canInteract(Vector3 pos, double maxDistance) {
        return this.canInteract(pos, maxDistance, 6.0);
    }

    public boolean canInteract(Vector3 pos, double maxDistance, double maxDiff) {
        if (this.distanceSquared(pos) > maxDistance * maxDistance) {
            return false;
        }

        Vector2 dV = this.getDirectionPlane();
        return (dV.dot(new Vector2(pos.x, pos.z)) - dV.dot(new Vector2(this.x, this.z))) >= -maxDiff;
    }

    private boolean canInteractEntity(Vector3 pos, double maxDistance) {
        if (this.distanceSquared(pos) > Math.pow(maxDistance, 2)) {
            return false;
        }

        Vector2 dV = this.getDirectionPlane();
        return (dV.dot(new Vector2(pos.x, pos.z)) - dV.dot(new Vector2(this.x, this.z))) >= -0.87;
    }

    protected void processLogin() {
        if (!this.server.isWhitelisted((this.username).toLowerCase())) {
            this.kick(PlayerKickEvent.Reason.NOT_WHITELISTED, this.getServer().getPropertyString("whitelist-reason").replace("§n", "\n"));
            return;
        } else if (this.isBanned()) {
            String reason = this.server.getNameBans().getEntires().get(this.getName().toLowerCase()).getReason();
            this.kick(PlayerKickEvent.Reason.NAME_BANNED, "You are banned!" + (reason.isEmpty() ? "" : (" Reason: " + reason)));
            return;
        } else if (!server.strongIPBans && this.server.getIPBans().isBanned(this.getAddress())) {
            this.kick(PlayerKickEvent.Reason.IP_BANNED, "Your IP is banned!");
            return;
        }

        for (Player p : new ArrayList<>(this.server.playerList.values())) {
            if (p != this && p.username != null) {
                if (p.username.equalsIgnoreCase(this.username) || this.getUniqueId().equals(p.getUniqueId())) {
                    p.close("", "disconnectionScreen.loggedinOtherLocation");
                    break;
                }
            }
        }

        CompoundTag nbt;
        String lowerName = this.username.toLowerCase();
        File legacyDataFile = new File(server.getDataPath() + "players/" + lowerName + ".dat");
        File dataFile = new File(server.getDataPath() + "players/" + this.uuid.toString() + ".dat");
        if (this.server.savePlayerDataByUuid) {
            boolean dataFound = dataFile.exists();
            if (!dataFound && legacyDataFile.exists()) {
                nbt = this.server.getOfflinePlayerData(lowerName, false);
                if (!legacyDataFile.delete()) {
                    this.server.getLogger().warning("Could not delete legacy player data for " + this.username);
                }
            } else {
                nbt = this.server.getOfflinePlayerData(this.uuid, !dataFound);
            }
        } else {
            boolean legacyMissing = !legacyDataFile.exists();
            if (legacyMissing && dataFile.exists()) {
                nbt = this.server.getOfflinePlayerData(this.uuid, false);
            } else {
                nbt = this.server.getOfflinePlayerData(lowerName, legacyMissing);
            }
        }

        if (nbt == null) {
            this.close(this.getLeaveMessage(), "Invalid data");
            return;
        }

        if (loginChainData.isXboxAuthed() || !server.xboxAuth) {
            server.updateName(this.uuid, this.username);
        }

        this.playedBefore = (nbt.getLong("lastPlayed") - nbt.getLong("firstPlayed")) > 1;

        nbt.putString("NameTag", this.username);

        this.setExperience(nbt.getInt("EXP"), nbt.getInt("expLevel"));

        if (this.server.getForceGamemode()) {
            this.gamemode = this.server.getGamemode();
            nbt.putInt("playerGameType", this.gamemode);
        } else {
            this.gamemode = nbt.getInt("playerGameType") & 0x03;
        }

        this.adventureSettings = new AdventureSettings(this)
                .set(Type.WORLD_IMMUTABLE, isAdventure() || isSpectator())
                .set(Type.WORLD_BUILDER, !isAdventure() && !isSpectator())
                .set(Type.AUTO_JUMP, true)
                .set(Type.ALLOW_FLIGHT, isCreative())
                .set(Type.NO_CLIP, isSpectator());

        Level level;
        if ((level = this.server.getLevelByName(nbt.getString("Level"))) == null || nbt.getShort("Health") < 1) {
            this.setLevel(this.server.getDefaultLevel());
            nbt.putString("Level", this.level.getName());
            nbt.getList("Pos", DoubleTag.class)
                    .add(new DoubleTag("0", this.level.getSpawnLocation().x))
                    .add(new DoubleTag("1", this.level.getSpawnLocation().y))
                    .add(new DoubleTag("2", this.level.getSpawnLocation().z));
        } else {
            this.setLevel(level);
        }

        for (Tag achievement : nbt.getCompound("Achievements").getAllTags()) {
            if (!(achievement instanceof ByteTag)) {
                continue;
            }

            if (((ByteTag) achievement).getData() > 0) {
                this.achievements.add(achievement.getName());
            }
        }

        nbt.putLong("lastPlayed", System.currentTimeMillis() / 1000);

        UUID uuid = getUniqueId();
        nbt.putLong("UUIDLeast", uuid.getLeastSignificantBits());
        nbt.putLong("UUIDMost", uuid.getMostSignificantBits());

        if (this.server.getAutoSave()) {
            if (this.server.savePlayerDataByUuid) {
                this.server.saveOfflinePlayerData(this.uuid, nbt, true);
            } else {
                this.server.saveOfflinePlayerData(this.username, nbt, true);
            }
        }

        this.sendPlayStatus(PlayStatusPacket.LOGIN_SUCCESS);

        ListTag<DoubleTag> posList = nbt.getList("Pos", DoubleTag.class);

        super.init(this.level.getChunk((int) posList.get(0).data >> 4, (int) posList.get(2).data >> 4, true), nbt);

        if (!this.namedTag.contains("foodLevel")) {
            this.namedTag.putInt("foodLevel", 20);
        }

        if (!this.namedTag.contains("FoodSaturationLevel")) {
            this.namedTag.putFloat("FoodSaturationLevel", 20);
        }

        this.foodData = new PlayerFood(this, this.namedTag.getInt("foodLevel"), this.namedTag.getFloat("foodSaturationLevel"));

        if (this.isSpectator()) this.keepMovement = true;

        this.forceMovement = this.teleportPosition = this.getPosition();

        ResourcePacksInfoPacket infoPacket = new ResourcePacksInfoPacket();
        infoPacket.resourcePackEntries = this.server.getResourcePackManager().getResourceStack();
        infoPacket.mustAccept = this.server.getForceResources();
        this.dataPacket(infoPacket);
    }

    protected void completeLoginSequence() {
        PlayerLoginEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerLoginEvent(this, "Plugin reason"));
        if (ev.isCancelled()) {
            this.close(this.getLeaveMessage(), ev.getKickMessage());
            return;
        }

        StartGamePacket startGamePacket = new StartGamePacket();
        startGamePacket.entityUniqueId = this.id;
        startGamePacket.entityRuntimeId = this.id;
        startGamePacket.playerGamemode = getClientFriendlyGamemode(this.gamemode);
        startGamePacket.x = (float) this.x;
        startGamePacket.y = (float) this.y;
        startGamePacket.z = (float) this.z;
        startGamePacket.yaw = (float) this.yaw;
        startGamePacket.pitch = (float) this.pitch;
        startGamePacket.dimension = this.getServer().dimensionsEnabled ? (byte) (this.level.getDimension() & 0xff) : 0;
        startGamePacket.worldGamemode = getClientFriendlyGamemode(this.gamemode);
        startGamePacket.difficulty = this.server.getDifficulty();
        startGamePacket.spawnX = (int) this.x;
        startGamePacket.spawnY = (int) this.y;
        startGamePacket.spawnZ = (int) this.z;
        startGamePacket.commandsEnabled = this.enableClientCommand;
        startGamePacket.gameRules = this.getLevel().getGameRules();
        startGamePacket.worldName = this.getServer().getNetwork().getName();
        this.dataPacket(startGamePacket);

        this.loggedIn = true;

        this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logIn",
                TextFormat.AQUA + this.username + TextFormat.WHITE,
                this.getAddress(),
                String.valueOf(this.getPort())));

        CompletableFuture.runAsync(() -> {
            try {
                if (!this.connected) return;
                if (this.protocol >= 313) {
                    if (this.protocol >= 361) {
                        this.dataPacket(new BiomeDefinitionListPacket());
                    }
                    this.dataPacket(new AvailableEntityIdentifiersPacket());
                }

                this.setImmobile(true);
                this.setCanClimb(true);
                this.setNameTagVisible(true);
                this.setNameTagAlwaysVisible(true);
                this.sendAttributes();
                this.adventureSettings.update();
                this.sendPotionEffects(this);
                this.sendData(this);
                this.sendAllInventories();

                if (this.protocol < 407) {
                    if (this.gamemode == Player.SPECTATOR) {
                        InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
                        inventoryContentPacket.inventoryId = ContainerIds.CREATIVE;
                        this.dataPacket(inventoryContentPacket);
                    } else {
                        this.inventory.sendCreativeContents();
                    }
                } else {
                    this.inventory.sendCreativeContents();
                }

                this.inventory.sendHeldItem(this);
                this.server.sendRecipeList(this);

                boolean op = this.isOp();

                if (!server.checkOpMovement && op) {
                    this.setCheckMovement(false);
                }

                if (op || this.hasPermission("nukkit.textcolor") || this.server.suomiCraftPEMode()) {
                    this.setRemoveFormat(false);
                }
            } catch (Exception e) {
                this.close("", "Internal Server Error");
                getServer().getLogger().logException(e);
            }
        });

        this.setEnableClientCommand(true);

        this.server.addOnlinePlayer(this);
        this.server.onPlayerCompleteLoginSequence(this);
    }

    public void handleDataPacket(DataPacket packet) {
        if (!connected) {
            return;
        }

        if (!loggedIn && !beforeLoginAvailablePackets.contains(packet.pid())) {
            server.getLogger().debug("Ignoring " + packet.getClass().getSimpleName() + " by " + username + " due to player not logged in yet");
            return;
        }

        packet.protocol = this.protocol;

        try (Timing ignore = Timings.getReceiveDataPacketTiming(packet)) {
            DataPacketReceiveEvent ev = new DataPacketReceiveEvent(this, packet);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return;
            }

            if (packet.pid() == ProtocolInfo.BATCH_PACKET) {
                this.server.getNetwork().processBatch((BatchPacket) packet, this);
                return;
            }

            if (Nukkit.DEBUG > 2 /*&& !server.isIgnoredPacket(packet.getClass())*/) {
                log.trace("Inbound {}: {}", this.getName(), packet);
            }

            packetswitch:
            switch (packet.pid()) {
                case ProtocolInfo.LOGIN_PACKET:
                    if (this.loggedIn) {
                        break;
                    }

                    LoginPacket loginPacket = (LoginPacket) packet;

                    this.protocol = loginPacket.getProtocol();

                    if (!ProtocolInfo.SUPPORTED_PROTOCOLS.contains(this.protocol)) {
                        this.close("", "You are running unsupported Minecraft version");
                        this.server.getLogger().debug(this.username + " disconnected with protocol " + this.protocol);
                        break;
                    }

                    if (this.protocol < getServer().getPropertyInt("multiversion-min-protocol")) {
                        this.close("", "Multiversion support for this Minecraft version is disabled");
                        this.server.getLogger().debug(this.username + " disconnected with protocol " + this.protocol);
                        break;
                    }

                    this.username = TextFormat.clean(loginPacket.username);
                    this.displayName = this.username;
                    this.iusername = this.username.toLowerCase();
                    this.setDataProperty(new StringEntityData(DATA_NAMETAG, this.username), false);

                    this.loginChainData = ClientChainData.read(loginPacket);

                    if (!loginChainData.isXboxAuthed() && server.xboxAuth) {
                        this.close("", "disconnectionScreen.notAuthenticated");
                        if (server.banAuthFailed) {
                            this.server.getNetwork().blockAddress(this.socketAddress.getAddress(), 5);
                            this.server.getLogger().notice("Blocked " + getAddress() + " for 5 seconds due to failed Xbox auth");
                        }
                        break;
                    }

                    if (this.server.getOnlinePlayersCount() >= this.server.getMaxPlayers() && this.kick(PlayerKickEvent.Reason.SERVER_FULL, "disconnectionScreen.serverFull", false)) {
                        break;
                    }

                    this.version = loginChainData.getGameVersion();

                    getServer().getLogger().debug("Name: " + this.username + " Protocol: " + this.protocol + " Version: " + this.version);

                    this.randomClientId = loginPacket.clientId;

                    this.uuid = loginPacket.clientUUID;
                    this.rawUUID = Binary.writeUUID(this.uuid);

                    boolean valid = true;
                    int len = loginPacket.username.length();
                    if (len > 16 || len < 3) {
                        valid = false;
                    }

                    if (valid) {
                        for (int i = 0; i < len; i++) {
                            char c = loginPacket.username.charAt(i);
                            if ((c >= 'a' && c <= 'z') ||
                                    (c >= 'A' && c <= 'Z') ||
                                    (c >= '0' && c <= '9') ||
                                    c == '_' || c == ' '
                            ) {
                                continue;
                            }

                            valid = false;
                            break;
                        }
                    }

                    if (!valid || Objects.equals(this.iusername, "rcon") || Objects.equals(this.iusername, "console")) {
                        this.close("", "disconnectionScreen.invalidName");
                        break;
                    }

                    if (!loginPacket.skin.isValid()) {
                        this.close("", "disconnectionScreen.invalidSkin");
                        break;
                    } else {
                        Skin skin = loginPacket.skin;
                        this.setSkin(skin.isPersona() && !this.getServer().personaSkins? Skin.NO_PERSONA_SKIN : skin);
                    }

                    PlayerPreLoginEvent playerPreLoginEvent;
                    this.server.getPluginManager().callEvent(playerPreLoginEvent = new PlayerPreLoginEvent(this, "Plugin reason"));
                    if (playerPreLoginEvent.isCancelled()) {
                        this.close("", playerPreLoginEvent.getKickMessage());
                        break;
                    }

                    Player playerInstance = this;
                    this.preLoginEventTask = new AsyncTask() {

                        private PlayerAsyncPreLoginEvent e;

                        @Override
                        public void onRun() {
                            e = new PlayerAsyncPreLoginEvent(username, uuid, Player.this.getAddress(), Player.this.getPort());
                            server.getPluginManager().callEvent(e);
                        }

                        @Override
                        public void onCompletion(Server server) {
                            if (!playerInstance.closed) {
                                if (e.getLoginResult() == LoginResult.KICK) {
                                    playerInstance.close(e.getKickMessage(), e.getKickMessage());
                                } else if (playerInstance.shouldLogin) {
                                    playerInstance.completeLoginSequence();

                                    for (Consumer<Server> action : e.getScheduledActions()) {
                                        action.accept(server);
                                    }
                                }
                            }
                        }
                    };

                    this.server.getScheduler().scheduleAsyncTask(this.preLoginEventTask);

                    this.processLogin();
                    break;
                case ProtocolInfo.RESOURCE_PACK_CLIENT_RESPONSE_PACKET:
                    ResourcePackClientResponsePacket responsePacket = (ResourcePackClientResponsePacket) packet;
                    switch (responsePacket.responseStatus) {
                        case ResourcePackClientResponsePacket.STATUS_REFUSED:
                            this.close("", "disconnectionScreen.noReason");
                            break;
                        case ResourcePackClientResponsePacket.STATUS_SEND_PACKS:
                            for (ResourcePackClientResponsePacket.Entry entry : responsePacket.packEntries) {
                                ResourcePack resourcePack = this.server.getResourcePackManager().getPackById(entry.uuid);
                                if (resourcePack == null) {
                                    this.close("", "disconnectionScreen.resourcePack");
                                    break;
                                }

                                ResourcePackDataInfoPacket dataInfoPacket = new ResourcePackDataInfoPacket();
                                dataInfoPacket.packId = resourcePack.getPackId();
                                dataInfoPacket.maxChunkSize = 1048576; //megabyte
                                dataInfoPacket.chunkCount = resourcePack.getPackSize() / dataInfoPacket.maxChunkSize;
                                dataInfoPacket.compressedPackSize = resourcePack.getPackSize();
                                dataInfoPacket.sha256 = resourcePack.getSha256();
                                this.dataPacket(dataInfoPacket);
                            }
                            break;
                        case ResourcePackClientResponsePacket.STATUS_HAVE_ALL_PACKS:
                            ResourcePackStackPacket stackPacket = new ResourcePackStackPacket();
                            stackPacket.mustAccept = this.server.getForceResources();
                            stackPacket.resourcePackStack = this.server.getResourcePackManager().getResourceStack();
                            this.dataPacket(stackPacket);
                            break;
                        case ResourcePackClientResponsePacket.STATUS_COMPLETED:
                            this.shouldLogin = true;

                            if (this.preLoginEventTask.isFinished()) {
                                this.preLoginEventTask.onCompletion(server);
                            }
                            break;
                    }
                    break;
                case ProtocolInfo.RESOURCE_PACK_CHUNK_REQUEST_PACKET:
                    ResourcePackChunkRequestPacket requestPacket = (ResourcePackChunkRequestPacket) packet;
                    ResourcePack resourcePack = this.server.getResourcePackManager().getPackById(requestPacket.packId);
                    if (resourcePack == null) {
                        this.close("", "disconnectionScreen.resourcePack");
                        break;
                    }

                    ResourcePackChunkDataPacket dataPacket = new ResourcePackChunkDataPacket();
                    dataPacket.packId = resourcePack.getPackId();
                    dataPacket.chunkIndex = requestPacket.chunkIndex;
                    dataPacket.data = resourcePack.getPackChunk(1048576 * requestPacket.chunkIndex, 1048576);
                    dataPacket.progress = 1048576L * requestPacket.chunkIndex;
                    this.dataPacket(dataPacket);
                    break;
                case ProtocolInfo.PLAYER_SKIN_PACKET:
                    PlayerSkinPacket skinPacket = (PlayerSkinPacket) packet;
                    Skin skin = skinPacket.skin;

                    if (!skin.isValid()) {
                        break;
                    }

                    PlayerChangeSkinEvent playerChangeSkinEvent = new PlayerChangeSkinEvent(this, skin);
                    playerChangeSkinEvent.setCancelled(TimeUnit.SECONDS.toMillis(this.server.getPlayerSkinChangeCooldown()) > System.currentTimeMillis() - this.lastSkinChange);
                    this.server.getPluginManager().callEvent(playerChangeSkinEvent);
                    if (!playerChangeSkinEvent.isCancelled()) {
                        this.lastSkinChange = System.currentTimeMillis();
                        this.setSkin(skin.isPersona() && !this.getServer().personaSkins? Skin.NO_PERSONA_SKIN : skin);
                    }
                    break;
                case ProtocolInfo.PLAYER_INPUT_PACKET:
                    if (!this.isAlive() || !this.spawned) {
                        break;
                    }
                    PlayerInputPacket ipk = (PlayerInputPacket) packet;
                    if (riding instanceof EntityMinecartAbstract) {
                        ((EntityMinecartAbstract) riding).setCurrentSpeed(ipk.motionY);
                    } else if (riding instanceof EntityHorseBase && !(riding instanceof EntityLlama)) {
                        ((EntityHorseBase) riding).onPlayerInput(this, ipk.motionX, ipk.motionY);
                    } else if (riding instanceof EntityPig) {
                        ((EntityPig) riding).onPlayerInput(this, ipk.motionX, ipk.motionY);
                    }
                    break;
                case ProtocolInfo.MOVE_PLAYER_PACKET:
                    if (this.teleportPosition != null) {
                        break;
                    }

                    MovePlayerPacket movePlayerPacket = (MovePlayerPacket) packet;

                    if (server.suomiCraftPEMode() && Math.abs(movePlayerPacket.pitch) > 90) {
                        this.kick(PlayerKickEvent.Reason.UNKNOWN, "Invalid MovePlayerPacket!");
                        break;
                    }

                    Vector3 newPos = new Vector3(movePlayerPacket.x, movePlayerPacket.y - this.getEyeHeight(), movePlayerPacket.z);
                    double dis = newPos.distanceSquared(this);

                    if (dis < 0.01 && movePlayerPacket.yaw % 360 == this.yaw && movePlayerPacket.pitch % 360 == this.pitch) {
                        break;
                    }

                    if (dis > 100) {
                        this.sendPosition(this, movePlayerPacket.yaw, movePlayerPacket.pitch, MovePlayerPacket.MODE_RESET);
                        break;
                    }

                    boolean revert = false;
                    if (!this.isAlive() || !this.spawned) {
                        revert = true;
                        this.forceMovement = this;
                    }

                    if (this.forceMovement != null && (newPos.distanceSquared(this.forceMovement) > 0.1 || revert)) {
                        this.sendPosition(this.forceMovement, movePlayerPacket.yaw, movePlayerPacket.pitch, MovePlayerPacket.MODE_RESET);
                    } else {

                        movePlayerPacket.yaw %= 360;
                        movePlayerPacket.pitch %= 360;

                        if (movePlayerPacket.yaw < 0) {
                            movePlayerPacket.yaw += 360;
                        }

                        this.setRotation(movePlayerPacket.yaw, movePlayerPacket.pitch);
                        this.newPosition = newPos;
                        this.forceMovement = null;
                    }

                    if (riding != null) {
                        if (riding instanceof EntityBoat) {
                            riding.setPositionAndRotation(this.temporalVector.setComponents(movePlayerPacket.x, movePlayerPacket.y - 1, movePlayerPacket.z), (movePlayerPacket.headYaw + 90) % 360, 0);
                        }
                    }

                    break;
                case ProtocolInfo.ADVENTURE_SETTINGS_PACKET:
                    AdventureSettingsPacket adventureSettingsPacket = (AdventureSettingsPacket) packet;
                    if (!server.getAllowFlight() && (adventureSettingsPacket.getFlag(AdventureSettingsPacket.ALLOW_FLIGHT) || adventureSettingsPacket.getFlag(AdventureSettingsPacket.FLYING)) && !this.adventureSettings.get(Type.ALLOW_FLIGHT)) {
                        this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
                        break;
                    }
                    PlayerToggleFlightEvent playerToggleFlightEvent = new PlayerToggleFlightEvent(this, adventureSettingsPacket.getFlag(AdventureSettingsPacket.FLYING));
                    this.server.getPluginManager().callEvent(playerToggleFlightEvent);
                    if (playerToggleFlightEvent.isCancelled()) {
                        this.adventureSettings.update();
                    } else {
                        this.adventureSettings.set(Type.FLYING, playerToggleFlightEvent.isFlying());
                    }
                    break;
                case ProtocolInfo.MOB_EQUIPMENT_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    MobEquipmentPacket mobEquipmentPacket = (MobEquipmentPacket) packet;

                    Inventory inv = this.getWindowById(mobEquipmentPacket.windowId);

                    if (inv == null) {
                        this.server.getLogger().debug("Player " + this.getName() + " has no open container with window ID " + mobEquipmentPacket.windowId);
                        return;
                    }

                    Item item = inv.getItem(mobEquipmentPacket.hotbarSlot);

                    if (!item.equals(mobEquipmentPacket.item)) {
                        this.server.getLogger().debug("Tried to equip " + mobEquipmentPacket.item + " but have " + item + " in target slot");
                        inv.sendContents(this);
                        return;
                    }

                    if (inv instanceof PlayerInventory) {
                        ((PlayerInventory) inv).equipItem(mobEquipmentPacket.hotbarSlot);
                    }

                    this.setDataFlag(Player.DATA_FLAGS, Player.DATA_FLAG_ACTION, false);

                    break;
                case ProtocolInfo.PLAYER_ACTION_PACKET:
                    PlayerActionPacket playerActionPacket = (PlayerActionPacket) packet;
                    if (!this.spawned || (!this.isAlive() && playerActionPacket.action != PlayerActionPacket.ACTION_RESPAWN && playerActionPacket.action != PlayerActionPacket.ACTION_DIMENSION_CHANGE_REQUEST)) {
                        break;
                    }

                    playerActionPacket.entityId = this.id;
                    Vector3 pos = new Vector3(playerActionPacket.x, playerActionPacket.y, playerActionPacket.z);
                    BlockFace face = BlockFace.fromIndex(playerActionPacket.face);

                    actionswitch:
                    switch (playerActionPacket.action) {
                        case PlayerActionPacket.ACTION_START_BREAK:
                            long currentBreak = System.currentTimeMillis();
                            BlockVector3 currentBreakPosition = new BlockVector3(playerActionPacket.x, playerActionPacket.y, playerActionPacket.z);
                            // HACK: Client spams multiple left clicks so we need to skip them.
                            if ((lastBreakPosition.equals(currentBreakPosition) && (currentBreak - this.lastBreak) < 10) || pos.distanceSquared(this) > 100) {
                                break;
                            }
                            Block target = this.level.getBlock(pos);
                            PlayerInteractEvent playerInteractEvent = new PlayerInteractEvent(this, this.inventory.getItemInHand(), target, face, target.getId() == 0 ? Action.LEFT_CLICK_AIR : Action.LEFT_CLICK_BLOCK);
                            this.getServer().getPluginManager().callEvent(playerInteractEvent);
                            if (playerInteractEvent.isCancelled()) {
                                this.inventory.sendHeldItem(this);
                                break;
                            }
                            switch (target.getId()) {
                                case Block.NOTEBLOCK:
                                    ((BlockNoteblock) target).emitSound();
                                    break actionswitch;
                                case Block.DRAGON_EGG:
                                    ((BlockDragonEgg) target).teleport();
                                    break actionswitch;
                            }
                            Block block = target.getSide(face);
                            if (block.getId() == Block.FIRE) {
                                this.level.setBlock(block, Block.get(BlockID.AIR), true);
                                this.level.addLevelSoundEvent(block, LevelSoundEventPacket.SOUND_EXTINGUISH_FIRE);
                                break;
                            }
                            if (!this.isCreative()) {
                                double breakTime = Math.ceil(target.getBreakTime(this.inventory.getItemInHand(), this) * 20);
                                if (breakTime > 0) {
                                    LevelEventPacket pk = new LevelEventPacket();
                                    pk.evid = LevelEventPacket.EVENT_BLOCK_START_BREAK;
                                    pk.x = (float) pos.x;
                                    pk.y = (float) pos.y;
                                    pk.z = (float) pos.z;
                                    pk.data = (int) (65535 / breakTime);
                                    this.getLevel().addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
                                }
                            }

                            this.breakingBlock = target;
                            this.lastBreak = currentBreak;
                            this.lastBreakPosition = currentBreakPosition;
                            break;

                        case PlayerActionPacket.ACTION_ABORT_BREAK:
                        case PlayerActionPacket.ACTION_STOP_BREAK:
                            LevelEventPacket pk = new LevelEventPacket();
                            pk.evid = LevelEventPacket.EVENT_BLOCK_STOP_BREAK;
                            pk.x = (float) pos.x;
                            pk.y = (float) pos.y;
                            pk.z = (float) pos.z;
                            pk.data = 0;
                            this.getLevel().addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
                            this.breakingBlock = null;
                            break;
                        case PlayerActionPacket.ACTION_GET_UPDATED_BLOCK:
                        case PlayerActionPacket.ACTION_DROP_ITEM:
                            break;
                        case PlayerActionPacket.ACTION_STOP_SLEEPING:
                            this.stopSleep();
                            break;
                        case PlayerActionPacket.ACTION_RESPAWN:
                            if (!this.spawned || this.isAlive() || !this.isOnline()) {
                                break;
                            }

                            if (this.server.isHardcore()) {
                                this.setBanned(true);
                                break;
                            }

                            this.craftingType = CRAFTING_SMALL;
                            this.resetCraftingGridType();

                            PlayerRespawnEvent playerRespawnEvent = new PlayerRespawnEvent(this, this.getSpawn());
                            this.server.getPluginManager().callEvent(playerRespawnEvent);

                            Position respawnPos = playerRespawnEvent.getRespawnPosition();

                            this.teleport(respawnPos, null);

                            if (this.protocol < 388) {
                                RespawnPacket respawnPacket = new RespawnPacket();
                                respawnPacket.x = (float) respawnPos.x;
                                respawnPacket.y = (float) respawnPos.y;
                                respawnPacket.z = (float) respawnPos.z;
                                this.dataPacket(respawnPacket);
                            }

                            this.sendExperience();
                            this.sendExperienceLevel();

                            this.setSprinting(false);
                            this.setSneaking(false);

                            this.extinguish();
                            this.setDataProperty(new ShortEntityData(Player.DATA_AIR, 400), false);
                            this.deadTicks = 0;
                            this.noDamageTicks = 60;

                            this.removeAllEffects();
                            this.setHealth(this.getMaxHealth());
                            this.foodData.setLevel(20, 20);

                            this.sendData(this);

                            this.setMovementSpeed(DEFAULT_SPEED);

                            this.adventureSettings.update();
                            this.inventory.sendContents(this);
                            this.inventory.sendArmorContents(this);
                            this.offhandInventory.sendContents(this);

                            this.spawnToAll();
                            this.scheduleUpdate();
                            break;
                        case PlayerActionPacket.ACTION_JUMP:
                            if (this.checkMovement && (this.inAirTicks > 30 || this.isSwimming() || this.isGliding()) && !server.getAllowFlight()) {
                                this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
                                break;
                            }
                            this.server.getPluginManager().callEvent(new PlayerJumpEvent(this));
                            break packetswitch;
                        case PlayerActionPacket.ACTION_START_SPRINT:
                            PlayerToggleSprintEvent playerToggleSprintEvent = new PlayerToggleSprintEvent(this, true);
                            this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                            if (playerToggleSprintEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSprinting(true);
                            }
                            break packetswitch;
                        case PlayerActionPacket.ACTION_STOP_SPRINT:
                            playerToggleSprintEvent = new PlayerToggleSprintEvent(this, false);
                            this.server.getPluginManager().callEvent(playerToggleSprintEvent);
                            if (playerToggleSprintEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSprinting(false);
                            }
                            break packetswitch;
                        case PlayerActionPacket.ACTION_START_SNEAK:
                            PlayerToggleSneakEvent playerToggleSneakEvent = new PlayerToggleSneakEvent(this, true);
                            this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                            if (playerToggleSneakEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSneaking(true);
                            }
                            break packetswitch;
                        case PlayerActionPacket.ACTION_STOP_SNEAK:
                            playerToggleSneakEvent = new PlayerToggleSneakEvent(this, false);
                            this.server.getPluginManager().callEvent(playerToggleSneakEvent);
                            if (playerToggleSneakEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSneaking(false);
                            }
                            break packetswitch;
                        case PlayerActionPacket.ACTION_DIMENSION_CHANGE_ACK:
                            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);
                            break;
                        case PlayerActionPacket.ACTION_START_GLIDE:
                            if (!server.getAllowFlight() && this.checkMovement) {
                                Item chestplate = this.getInventory().getChestplateFast();
                                if ((chestplate == null || chestplate.getId() != ItemID.ELYTRA) && !server.getAllowFlight()) {
                                    this.kick(PlayerKickEvent.Reason.FLYING_DISABLED, "Flying is not enabled on this server");
                                    break;
                                }
                            }
                            PlayerToggleGlideEvent playerToggleGlideEvent = new PlayerToggleGlideEvent(this, true);
                            this.server.getPluginManager().callEvent(playerToggleGlideEvent);
                            if (playerToggleGlideEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setGliding(true);
                            }
                            break packetswitch;
                        case PlayerActionPacket.ACTION_STOP_GLIDE:
                            playerToggleGlideEvent = new PlayerToggleGlideEvent(this, false);
                            this.server.getPluginManager().callEvent(playerToggleGlideEvent);
                            if (playerToggleGlideEvent.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setGliding(false);
                            }
                            break packetswitch;
                        case PlayerActionPacket.ACTION_CONTINUE_BREAK:
                            if (this.isBreakingBlock()) {
                                block = this.level.getBlock(pos);
                                this.level.addParticle(new PunchBlockParticle(pos, block, face));
                            }
                            break;
                        case PlayerActionPacket.ACTION_START_SWIMMING:
                            PlayerToggleSwimEvent ptse = new PlayerToggleSwimEvent(this, true);
                            this.server.getPluginManager().callEvent(ptse);
                            if (ptse.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSwimming(true);
                            }
                            break;
                        case PlayerActionPacket.ACTION_STOP_SWIMMING:
                            ptse = new PlayerToggleSwimEvent(this, false);
                            this.server.getPluginManager().callEvent(ptse);
                            if (ptse.isCancelled()) {
                                this.sendData(this);
                            } else {
                                this.setSwimming(false);
                            }
                            break;
                    }

                    this.setUsingItem(false);
                    break;
                case ProtocolInfo.MODAL_FORM_RESPONSE_PACKET:
                    this.formOpen = false;

                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    ModalFormResponsePacket modalFormPacket = (ModalFormResponsePacket) packet;

                    if (formWindows.containsKey(modalFormPacket.formId)) {
                        FormWindow window = formWindows.remove(modalFormPacket.formId);
                        window.setResponse(modalFormPacket.data.trim());

                        PlayerFormRespondedEvent event = new PlayerFormRespondedEvent(this, modalFormPacket.formId, window);
                        getServer().getPluginManager().callEvent(event);
                    } else if (serverSettings.containsKey(modalFormPacket.formId)) {
                        FormWindow window = serverSettings.get(modalFormPacket.formId);
                        window.setResponse(modalFormPacket.data.trim());

                        PlayerSettingsRespondedEvent event = new PlayerSettingsRespondedEvent(this, modalFormPacket.formId, window);
                        getServer().getPluginManager().callEvent(event);

                        if (!event.isCancelled() && window instanceof FormWindowCustom)
                            ((FormWindowCustom) window).setElementsFromResponse();
                    }

                    break;

                case ProtocolInfo.INTERACT_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    this.craftingType = CRAFTING_SMALL;

                    InteractPacket interactPacket = (InteractPacket) packet;

                    Entity targetEntity = this.level.getEntity(interactPacket.target);

                    if (interactPacket.action != InteractPacket.ACTION_OPEN_INVENTORY && (targetEntity == null || !this.isAlive() || !targetEntity.isAlive())) {
                        break;
                    }

                    if (interactPacket.action != InteractPacket.ACTION_OPEN_INVENTORY && (targetEntity instanceof EntityItem || targetEntity instanceof EntityArrow || targetEntity instanceof EntityXPOrb)) {
                        //this.kick(PlayerKickEvent.Reason.INVALID_PVE, "Attempting to interact with an invalid entity");
                        this.server.getLogger().warning(this.getServer().getLanguage().translateString("nukkit.player.invalidEntity", this.username));
                        break;
                    }

                    switch (interactPacket.action) {
                        case InteractPacket.ACTION_OPEN_INVENTORY:
                            if (this.protocol >= 407 && this.spawned) {
                                this.inventory.sendInventory();
                            }
                            break;
                        case InteractPacket.ACTION_MOUSEOVER:
                            if (interactPacket.target == 0 && this.protocol >= 313) {
                                break packetswitch;
                            }
                            this.getServer().getPluginManager().callEvent(new PlayerMouseOverEntityEvent(this, targetEntity));
                            break;
                        case InteractPacket.ACTION_VEHICLE_EXIT:
                            if (!(targetEntity instanceof EntityRideable) || this.riding == null) {
                                break;
                            }

                            ((EntityRideable) riding).mountEntity(this);
                            break;
                    }
                    break;
                case ProtocolInfo.BLOCK_PICK_REQUEST_PACKET:
                    BlockPickRequestPacket pickRequestPacket = (BlockPickRequestPacket) packet;
                    Block block = this.level.getBlock(this.temporalVector.setComponents(pickRequestPacket.x, pickRequestPacket.y, pickRequestPacket.z), false);
                    item = block.toItem();
                    if (pickRequestPacket.addUserData) {
                        BlockEntity blockEntity = this.getLevel().getBlockEntity(new Vector3(pickRequestPacket.x, pickRequestPacket.y, pickRequestPacket.z));
                        if (blockEntity != null) {
                            CompoundTag nbt = blockEntity.getCleanedNBT();
                            if (nbt != null) {
                                item.setCustomBlockData(nbt);
                                item.setLore("+(DATA)");
                            }
                        }
                    }

                    PlayerBlockPickEvent pickEvent = new PlayerBlockPickEvent(this, block, item);
                    if (this.isSpectator()) {
                        pickEvent.setCancelled();
                    }

                    this.server.getPluginManager().callEvent(pickEvent);

                    if (!pickEvent.isCancelled()) {
                        boolean itemExists = false;
                        int itemSlot = -1;
                        for (int slot = 0; slot < this.inventory.getSize(); slot++) {
                            if (this.inventory.getItem(slot).equals(pickEvent.getItem())) {
                                if (slot < this.inventory.getHotbarSize()) {
                                    this.inventory.setHeldItemSlot(slot);
                                } else {
                                    itemSlot = slot;
                                }
                                itemExists = true;
                                break;
                            }
                        }

                        for (int slot = 0; slot < this.inventory.getHotbarSize(); slot++) {
                            if (this.inventory.getItem(slot).isNull()) {
                                if (!itemExists && this.isCreative()) {
                                    this.inventory.setHeldItemSlot(slot);
                                    this.inventory.setItemInHand(pickEvent.getItem());
                                    break packetswitch;
                                } else if (itemSlot > -1) {
                                    this.inventory.setHeldItemSlot(slot);
                                    this.inventory.setItemInHand(this.inventory.getItem(itemSlot));
                                    this.inventory.clear(itemSlot, true);
                                    break packetswitch;
                                }
                            }
                        }

                        if (!itemExists && this.isCreative()) {
                            Item itemInHand = this.inventory.getItemInHand();
                            this.inventory.setItemInHand(pickEvent.getItem());
                            if (!this.inventory.isFull()) {
                                for (int slot = 0; slot < this.inventory.getSize(); slot++) {
                                    if (this.inventory.getItem(slot).isNull()) {
                                        this.inventory.setItem(slot, itemInHand);
                                        break;
                                    }
                                }
                            }
                        } else if (itemSlot > -1) {
                            Item itemInHand = this.inventory.getItemInHand();
                            this.inventory.setItemInHand(this.inventory.getItem(itemSlot));
                            this.inventory.setItem(itemSlot, itemInHand);
                        }
                    }
                    break;
                case ProtocolInfo.ANIMATE_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    PlayerAnimationEvent animationEvent = new PlayerAnimationEvent(this, ((AnimatePacket) packet).action);
                    this.server.getPluginManager().callEvent(animationEvent);
                    if (animationEvent.isCancelled()) {
                        break;
                    }

                    AnimatePacket.Action animation = animationEvent.getAnimationType();

                    switch (animation) {
                        case ROW_RIGHT:
                        case ROW_LEFT:
                            if (this.riding instanceof EntityBoat) {
                                ((EntityBoat) this.riding).onPaddle(animation, ((AnimatePacket) packet).rowingTime);
                            }
                            break;
                    }

                    AnimatePacket animatePacket = new AnimatePacket();
                    animatePacket.eid = this.getId();
                    animatePacket.action = animationEvent.getAnimationType();
                    Server.broadcastPacket(this.getViewers().values(), animatePacket);
                    break;
                case ProtocolInfo.ENTITY_EVENT_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    EntityEventPacket entityEventPacket = (EntityEventPacket) packet;

                    if (entityEventPacket.event != EntityEventPacket.ENCHANT) {
                        this.craftingType = CRAFTING_SMALL;
                    }

                    switch (entityEventPacket.event) {
                        case EntityEventPacket.EATING_ITEM:
                            if (entityEventPacket.data == 0 || entityEventPacket.eid != this.id) {
                                break;
                            }

                            entityEventPacket.eid = this.id;
                            entityEventPacket.isEncoded = false;
                            this.dataPacket(entityEventPacket);
                            Server.broadcastPacket(this.getViewers().values(), entityEventPacket);
                            break;
                        case EntityEventPacket.ENCHANT:
                            if (this.protocol >= ProtocolInfo.v1_16_0) {
                                break;
                            }

                            if (entityEventPacket.eid != this.id) {
                                break;
                            }

                            int levels = entityEventPacket.data; // Sent as negative number of levels lost
                            if (levels < 0) {
                                this.setExperience(this.exp, this.expLevel + levels);
                            }
                            break;
                    }
                    break;
                case ProtocolInfo.COMMAND_REQUEST_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }
                    this.craftingType = CRAFTING_SMALL;
                    CommandRequestPacket commandRequestPacket = (CommandRequestPacket) packet;
                    PlayerCommandPreprocessEvent playerCommandPreprocessEvent = new PlayerCommandPreprocessEvent(this, commandRequestPacket.command);
                    this.server.getPluginManager().callEvent(playerCommandPreprocessEvent);
                    if (playerCommandPreprocessEvent.isCancelled()) {
                        break;
                    }

                    if (Timings.playerCommandTimer != null) Timings.playerCommandTimer.startTiming();
                    this.server.dispatchCommand(playerCommandPreprocessEvent.getPlayer(), playerCommandPreprocessEvent.getMessage().substring(1));
                    if (Timings.playerCommandTimer != null) Timings.playerCommandTimer.stopTiming();
                    break;
                case ProtocolInfo.TEXT_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }

                    TextPacket textPacket = (TextPacket) packet;

                    if (textPacket.type == TextPacket.TYPE_CHAT) {
                        String chatMessage = textPacket.message;
                        int breakLine = chatMessage.indexOf('\n');
                        // Chat messages shouldn't contain break lines so ignore text afterwards
                        if (breakLine != -1) {
                            chatMessage = chatMessage.substring(0, breakLine);
                        }
                        this.chat(chatMessage);
                    }
                    break;
                case ProtocolInfo.CONTAINER_CLOSE_PACKET:
                    ContainerClosePacket containerClosePacket = (ContainerClosePacket) packet;
                    if (!this.spawned || (containerClosePacket.windowId == 0 && this.protocol >= 407)) {
                        this.inventory.closeInventory();
                        break;
                    }

                    if (this.windowIndex.containsKey(containerClosePacket.windowId)) {
                        this.server.getPluginManager().callEvent(new InventoryCloseEvent(this.windowIndex.get(containerClosePacket.windowId), this));
                        this.removeWindow(this.windowIndex.get(containerClosePacket.windowId));
                    } else {
                        this.windowIndex.remove(containerClosePacket.windowId);
                    }
                    if (containerClosePacket.windowId == -1) {
                        this.craftingType = CRAFTING_SMALL;
                        this.resetCraftingGridType();
                        this.addWindow(this.craftingGrid, ContainerIds.NONE);
                        if (protocol >= 407) {
                            ContainerClosePacket pk = new ContainerClosePacket();
                            pk.windowId = -1;
                            this.dataPacket(pk);
                        }
                    }
                    break;
                case ProtocolInfo.BLOCK_ENTITY_DATA_PACKET:
                    if (!this.spawned || !this.isAlive()) {
                        break;
                    }
                    BlockEntityDataPacket blockEntityDataPacket = (BlockEntityDataPacket) packet;
                    this.craftingType = CRAFTING_SMALL;
                    this.resetCraftingGridType();

                    pos = new Vector3(blockEntityDataPacket.x, blockEntityDataPacket.y, blockEntityDataPacket.z);
                    if (pos.distanceSquared(this) > 10000) {
                        break;
                    }

                    BlockEntity t = this.level.getBlockEntity(pos);
                    if (t instanceof BlockEntitySpawnable) {
                        CompoundTag nbt;
                        try {
                            nbt = NBTIO.read(blockEntityDataPacket.namedTag, ByteOrder.LITTLE_ENDIAN, true);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                        if (!((BlockEntitySpawnable) t).updateCompoundTag(nbt, this)) {
                            ((BlockEntitySpawnable) t).spawnTo(this);
                        }
                    }
                    break;
                case ProtocolInfo.REQUEST_CHUNK_RADIUS_PACKET:
                    RequestChunkRadiusPacket requestChunkRadiusPacket = (RequestChunkRadiusPacket) packet;
                    ChunkRadiusUpdatedPacket chunkRadiusUpdatePacket = new ChunkRadiusUpdatedPacket();
                    this.chunkRadius = Math.max(3, Math.min(requestChunkRadiusPacket.radius, this.viewDistance));
                    chunkRadiusUpdatePacket.radius = this.chunkRadius;
                    this.dataPacket(chunkRadiusUpdatePacket);
                    break;
                case ProtocolInfo.SET_PLAYER_GAME_TYPE_PACKET:
                    SetPlayerGameTypePacket setPlayerGameTypePacket = (SetPlayerGameTypePacket) packet;
                    if (setPlayerGameTypePacket.gamemode != this.gamemode) {
                        if (!this.hasPermission("nukkit.command.gamemode")) {
                            SetPlayerGameTypePacket setPlayerGameTypePacket1 = new SetPlayerGameTypePacket();
                            setPlayerGameTypePacket1.gamemode = this.gamemode & 0x01;
                            this.dataPacket(setPlayerGameTypePacket1);
                            this.adventureSettings.update();
                            break;
                        }
                        this.setGamemode(setPlayerGameTypePacket.gamemode, true);
                        Command.broadcastCommandMessage(this, new TranslationContainer("commands.gamemode.success.self", Server.getGamemodeString(this.gamemode)));
                    }
                    break;
                case ProtocolInfo.ITEM_FRAME_DROP_ITEM_PACKET:
                    ItemFrameDropItemPacket itemFrameDropItemPacket = (ItemFrameDropItemPacket) packet;
                    Vector3 vector3 = this.temporalVector.setComponents(itemFrameDropItemPacket.x, itemFrameDropItemPacket.y, itemFrameDropItemPacket.z);
                    BlockEntity blockEntityItemFrame = this.level.getBlockEntity(vector3);
                    BlockEntityItemFrame itemFrame = (BlockEntityItemFrame) blockEntityItemFrame;
                    if (itemFrame != null) {
                        block = itemFrame.getBlock();
                        Item itemDrop = itemFrame.getItem();
                        ItemFrameDropItemEvent itemFrameDropItemEvent = new ItemFrameDropItemEvent(this, block, itemFrame, itemDrop);
                        this.server.getPluginManager().callEvent(itemFrameDropItemEvent);
                        if (!itemFrameDropItemEvent.isCancelled()) {
                            if (itemDrop.getId() != Item.AIR) {
                                vector3 = this.temporalVector.setComponents(itemFrame.x + 0.5, itemFrame.y, itemFrame.z + 0.5);
                                this.level.dropItem(vector3, itemDrop);
                                itemFrame.setItem(new ItemBlock(Block.get(BlockID.AIR)));
                                itemFrame.setItemRotation(0);
                                this.getLevel().addSoundToViewers(this, Sound.BLOCK_ITEMFRAME_REMOVE_ITEM);
                            }
                        } else {
                            itemFrame.spawnTo(this);
                        }
                    }
                    break;
                case ProtocolInfo.MAP_INFO_REQUEST_PACKET:
                    MapInfoRequestPacket pk = (MapInfoRequestPacket) packet;
                    Item mapItem = null;

                    for (Item item1 : this.inventory.getContents().values()) {
                        if (item1 instanceof ItemMap && ((ItemMap) item1).getMapId() == pk.mapId) {
                            mapItem = item1;
                        }
                    }

                    if (mapItem == null) {
                        for (BlockEntity be : this.level.getBlockEntities().values()) {
                            if (be instanceof BlockEntityItemFrame) {
                                BlockEntityItemFrame itemFrame1 = (BlockEntityItemFrame) be;

                                if (itemFrame1.getItem() instanceof ItemMap && ((ItemMap) itemFrame1.getItem()).getMapId() == pk.mapId) {
                                    ((ItemMap) itemFrame1.getItem()).sendImage(this);
                                    break;
                                }
                            }
                        }
                    }

                    if (mapItem != null) {
                        PlayerMapInfoRequestEvent event;
                        getServer().getPluginManager().callEvent(event = new PlayerMapInfoRequestEvent(this, mapItem));

                        if (!event.isCancelled()) {
                            try {
                                BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
                                Graphics2D graphics = image.createGraphics();

                                for (int x = 0; x < 128; x++) {
                                    for (int y = 0; y < 128; y++) {
                                        graphics.setColor(new Color(this.getLevel().getMapColorAt(this.getFloorX() - 64 + x, this.getFloorZ() - 64 + y).getRGB()));
                                        graphics.fillRect(x, y, x, y);
                                    }
                                }

                                ((ItemMap) mapItem).setImage(image);
                                ((ItemMap) mapItem).sendImage(this);
                            } catch (Exception ex) {
                                this.getServer().getLogger().debug("There was an error while generating map image", ex);
                            }
                        }
                    }

                    break;
                case ProtocolInfo.LEVEL_SOUND_EVENT_PACKET:
                case ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V1:
                case ProtocolInfo.LEVEL_SOUND_EVENT_PACKET_V2:
                    if (this.isSpectator()) {
                        if (((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_HIT || ((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_ATTACK_NODAMAGE || ((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_ATTACK || ((LevelSoundEventPacket) packet).sound == LevelSoundEventPacket.SOUND_ATTACK_STRONG) {
                            break;
                        }
                    }
                    this.level.addChunkPacket(this.getChunkX(), this.getChunkZ(), packet);
                    break;
                case ProtocolInfo.INVENTORY_TRANSACTION_PACKET:
                    if (this.isSpectator()) {
                        this.sendAllInventories();
                        break;
                    }

                    InventoryTransactionPacket transactionPacket = (InventoryTransactionPacket) packet;

                    List<InventoryAction> actions = new ArrayList<>();
                    for (NetworkInventoryAction networkInventoryAction : transactionPacket.actions) {
                        InventoryAction a = networkInventoryAction.createInventoryAction(this);

                        if (a == null) {
                            this.getServer().getLogger().debug("Unmatched inventory action from " + this.username + ": " + networkInventoryAction);
                            this.sendAllInventories();
                            break packetswitch;
                        }

                        actions.add(a);
                    }

                    if (transactionPacket.isCraftingPart) {
                        if (this.craftingTransaction == null) {
                            this.craftingTransaction = new CraftingTransaction(this, actions);
                        } else {
                            for (InventoryAction action : actions) {
                                this.craftingTransaction.addAction(action);
                            }
                        }

                        if (this.craftingTransaction.getPrimaryOutput() != null && this.craftingTransaction.canExecute()) {
                            try {
                                this.craftingTransaction.execute();
                            } catch (Exception e) {
                                this.server.getLogger().debug("Executing crafting transaction failed");
                            }
                            this.craftingTransaction = null;
                        }
                        return;
                    } else if (this.protocol >= ProtocolInfo.v1_16_0 && transactionPacket.isEnchantingPart) {
                        if (this.enchantTransaction == null) {
                            this.enchantTransaction = new EnchantTransaction(this, actions);
                        } else {
                            for (InventoryAction action : actions) {
                                this.enchantTransaction.addAction(action);
                            }
                        }
                        if (this.enchantTransaction.canExecute()) {
                            this.enchantTransaction.execute();
                            this.enchantTransaction = null;
                        }
                        return;
                    } else if (this.craftingTransaction != null) {
                        if (craftingTransaction.checkForCraftingPart(actions)) {
                            for (InventoryAction action : actions) {
                                craftingTransaction.addAction(action);
                            }
                            return;
                        } else {
                            this.server.getLogger().debug("Got unexpected normal inventory action with incomplete crafting transaction from " + this.username + ", refusing to execute crafting");
                            if (this.protocol >= ProtocolInfo.v1_16_0) {
                                this.removeAllWindows(false);
                                this.sendAllInventories();
                            }
                            this.craftingTransaction = null;
                        }
                    } else if (this.protocol >= ProtocolInfo.v1_16_0 && this.enchantTransaction != null) {
                        if (enchantTransaction.checkForEnchantPart(actions)) {
                            for (InventoryAction action : actions) {
                                enchantTransaction.addAction(action);
                            }
                            return;
                        } else {
                            this.server.getLogger().debug("Got unexpected normal inventory action with incomplete enchanting transaction from " + this.username + ", refusing to execute enchant " + transactionPacket.toString());
                            this.removeAllWindows(false);
                            this.sendAllInventories();
                            this.enchantTransaction = null;
                        }
                    }

                    switch (transactionPacket.transactionType) {
                        case InventoryTransactionPacket.TYPE_NORMAL:
                            InventoryTransaction transaction = new InventoryTransaction(this, actions);

                            if (!transaction.execute()) {
                                this.server.getLogger().debug("Failed to execute inventory transaction from " + this.username + " with actions: " + Arrays.toString(transactionPacket.actions));
                                failedTransactions++;
                                if (failedTransactions > 10) {
                                    this.close("", "Too many failed inventory transactions");
                                }
                                break packetswitch;
                            }

                            break packetswitch;
                        case InventoryTransactionPacket.TYPE_MISMATCH:
                            if (transactionPacket.actions.length > 0) {
                                this.server.getLogger().debug("Expected 0 actions for mismatch, got " + transactionPacket.actions.length + ", " + Arrays.toString(transactionPacket.actions));
                            }
                            this.sendAllInventories();

                            break packetswitch;
                        case InventoryTransactionPacket.TYPE_USE_ITEM:
                            UseItemData useItemData;
                            BlockVector3 blockVector;
                            int type;

                            try {
                                useItemData = (UseItemData) transactionPacket.transactionData;
                                blockVector = useItemData.blockPos;
                                face = useItemData.face;
                                type = useItemData.actionType;
                            } catch (Exception ignored) {
                                break packetswitch;
                            }

                            switch (type) {
                                case InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_BLOCK:
                                    // Hack: Fix client spamming right clicks
                                    if (!server.doNotLimitInteractions && (lastRightClickPos != null && System.currentTimeMillis() - lastRightClickTime < 200.0 && blockVector.distanceSquared(lastRightClickPos) < 0.00001)) {
                                        return;
                                    }

                                    lastRightClickPos = blockVector.asVector3();
                                    lastRightClickTime = System.currentTimeMillis();

                                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);

                                    if (!(this.distance(blockVector.asVector3()) > (this.isCreative() ? 13 : 7))) {
                                        if (this.isCreative()) {
                                            if (this.level.useItemOn(blockVector.asVector3(), inventory.getItemInHand(), face, useItemData.clickPos.x, useItemData.clickPos.y, useItemData.clickPos.z, this) != null) {
                                                break packetswitch;
                                            }
                                        } else if (inventory.getItemInHand().equals(useItemData.itemInHand)) {
                                            Item i = inventory.getItemInHand();
                                            Item oldItem = i.clone();
                                            if ((i = this.level.useItemOn(blockVector.asVector3(), i, face, useItemData.clickPos.x, useItemData.clickPos.y, useItemData.clickPos.z, this)) != null) {
                                                if (!i.equals(oldItem) || i.getCount() != oldItem.getCount()) {
                                                    if (oldItem.getId() == i.getId() || i.getId() == 0) {
                                                        inventory.setItemInHand(i);
                                                    } else {
                                                        server.getLogger().debug("Tried to set item " + i.getId() + " but " + this.username + " had item " + oldItem.getId() + " in their hand slot");
                                                    }
                                                    inventory.sendHeldItem(this.getViewers().values());
                                                }
                                                break packetswitch;
                                            }
                                        }
                                    }

                                    inventory.sendHeldItem(this);

                                    if (blockVector.distanceSquared(this) > 10000) {
                                        break packetswitch;
                                    }

                                    Block target = this.level.getBlock(blockVector.asVector3());
                                    block = target.getSide(face);

                                    this.level.sendBlocks(this, new Block[]{target, block}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

                                    if (target instanceof BlockDoor) {
                                        BlockDoor door = (BlockDoor) target;

                                        Block part;

                                        if ((door.getDamage() & 0x08) > 0) {
                                            part = target.down();

                                            if (part.getId() == target.getId()) {
                                                target = part;

                                                this.level.sendBlocks(this, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);
                                            }
                                        }
                                    }
                                    break packetswitch;
                                case InventoryTransactionPacket.USE_ITEM_ACTION_BREAK_BLOCK:
                                    if (!this.spawned || !this.isAlive()) {
                                        break packetswitch;
                                    }

                                    this.resetCraftingGridType();

                                    Item i = this.getInventory().getItemInHand();

                                    Item oldItem = i.clone();

                                    if (this.canInteract(blockVector.add(0.5, 0.5, 0.5), this.isCreative() ? 13 : 7) && (i = this.level.useBreakOn(blockVector.asVector3(), face, i, this, true)) != null) {
                                        if (this.isSurvival() || this.isAdventure()) {
                                            this.foodData.updateFoodExpLevel(0.025);
                                            if (!i.equals(oldItem) || i.getCount() != oldItem.getCount()) {
                                                if (oldItem.getId() == i.getId() || i.getId() == 0) {
                                                    inventory.setItemInHand(i);
                                                } else {
                                                    server.getLogger().debug("Tried to set item " + i.getId() + " but " + this.username + " had item " + oldItem.getId() + " in their hand slot");
                                                }
                                                inventory.sendHeldItem(this.getViewers().values());
                                            }
                                        }
                                        break packetswitch;
                                    }

                                    inventory.sendContents(this);
                                    target = this.level.getBlock(blockVector.asVector3());
                                    BlockEntity blockEntity = this.level.getBlockEntity(blockVector.asVector3());

                                    this.level.sendBlocks(this, new Block[]{target}, UpdateBlockPacket.FLAG_ALL_PRIORITY);

                                    inventory.sendHeldItem(this);

                                    if (blockEntity instanceof BlockEntitySpawnable) {
                                        ((BlockEntitySpawnable) blockEntity).spawnTo(this);
                                    }

                                    break packetswitch;
                                case InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR:
                                    Vector3 directionVector = this.getDirectionVector();

                                    if (this.isCreative()) {
                                        item = this.inventory.getItemInHand();
                                    } else if (!this.inventory.getItemInHand().equals(useItemData.itemInHand)) {
                                        this.inventory.sendHeldItem(this);
                                        break packetswitch;
                                    } else {
                                        item = this.inventory.getItemInHand();
                                    }

                                    PlayerInteractEvent interactEvent = new PlayerInteractEvent(this, item, directionVector, face, Action.RIGHT_CLICK_AIR);

                                    this.server.getPluginManager().callEvent(interactEvent);

                                    if (interactEvent.isCancelled()) {
                                        this.inventory.sendHeldItem(this);
                                        break packetswitch;
                                    }

                                    if (item.onClickAir(this, directionVector)) {
                                        if (this.isSurvival() || this.isAdventure()) {
                                            if (this.inventory.getItemInHand().getId() == item.getId() || item.getId() == 0) {
                                                this.inventory.setItemInHand(item);
                                            } else {
                                                server.getLogger().debug("Tried to set item " + item.getId() + " but " + this.username + " had item " + this.inventory.getItemInHand().getId() + " in their hand slot");
                                            }
                                        }

                                        if (!this.isUsingItem()) {
                                            this.setUsingItem(true);
                                            break packetswitch;
                                        }

                                        // Used item
                                        int ticksUsed = this.server.getTick() - this.startAction;
                                        this.setUsingItem(false);
                                        if (!item.onUse(this, ticksUsed)) {
                                            this.inventory.sendContents(this);
                                        }
                                    }

                                    break packetswitch;
                                default:
                                    break;
                            }
                            break;
                        case InventoryTransactionPacket.TYPE_USE_ITEM_ON_ENTITY:
                            UseItemOnEntityData useItemOnEntityData = (UseItemOnEntityData) transactionPacket.transactionData;

                            Entity target = this.level.getEntity(useItemOnEntityData.entityRuntimeId);
                            if (target == null) {
                                return;
                            }

                            type = useItemOnEntityData.actionType;

                            if (!useItemOnEntityData.itemInHand.equalsExact(this.inventory.getItemInHand())) {
                                this.inventory.sendHeldItem(this);
                            }

                            item = this.inventory.getItemInHand();

                            switch (type) {
                                case InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_INTERACT:
                                    PlayerInteractEntityEvent playerInteractEntityEvent = new PlayerInteractEntityEvent(this, target, item, useItemOnEntityData.clickPos);
                                    if (this.isSpectator()) playerInteractEntityEvent.setCancelled();
                                    getServer().getPluginManager().callEvent(playerInteractEntityEvent);

                                    if (playerInteractEntityEvent.isCancelled()) {
                                        break;
                                    }

                                    if (target.onInteract(this, item, useItemOnEntityData.clickPos) && (this.isSurvival() || this.isAdventure())) {
                                        if (item.isTool()) {
                                            if (item.useOn(target) && item.getDamage() >= item.getMaxDurability()) {
                                                item = new ItemBlock(Block.get(BlockID.AIR));
                                            }
                                        } else {
                                            if (item.count > 1) {
                                                item.count--;
                                            } else {
                                                item = new ItemBlock(Block.get(BlockID.AIR));
                                            }
                                        }

                                        if (this.inventory.getItemInHand().getId() == item.getId() || item.getId() == 0) {
                                            this.inventory.setItemInHand(item);
                                        } else {
                                            server.getLogger().debug("Tried to set item " + item.getId() + " but " + this.username + " had item " + this.inventory.getItemInHand().getId() + " in their hand slot");
                                        }
                                    }
                                    break;
                                case InventoryTransactionPacket.USE_ITEM_ON_ENTITY_ACTION_ATTACK:
                                    float itemDamage = item.getAttackDamage();

                                    for (Enchantment enchantment : item.getEnchantments()) {
                                        itemDamage += enchantment.getDamageBonus(target);
                                    }

                                    Map<DamageModifier, Float> damage = new EnumMap<>(DamageModifier.class);
                                    damage.put(DamageModifier.BASE, itemDamage);

                                    if (!this.canInteractEntity(target, isCreative() ? 8 : 5)) {
                                        break;
                                    } else if (target instanceof Player) {
                                        if ((((Player) target).gamemode & 0x01) > 0) {
                                            break;
                                        } else if (!this.server.pvp) {
                                            break;
                                        }
                                    }

                                    EntityDamageByEntityEvent entityDamageByEntityEvent = new EntityDamageByEntityEvent(this, target, DamageCause.ENTITY_ATTACK, damage);
                                    if (this.isSpectator()) entityDamageByEntityEvent.setCancelled();
                                    if ((target instanceof Player) && !this.level.getGameRules().getBoolean(GameRule.PVP)) {
                                        entityDamageByEntityEvent.setCancelled();
                                    }

                                    if (!entityDamageByEntityEvent.isCancelled() && this.isBlocking()) {
                                        this.setBlocking(false);
                                    }

                                    if (!target.attack(entityDamageByEntityEvent)) {
                                        if (item.isTool() && !this.isCreative()) {
                                            this.inventory.sendContents(this);
                                        }
                                        break;
                                    }

                                    for (Enchantment enchantment : item.getEnchantments()) {
                                        enchantment.doPostAttack(this, target);
                                    }

                                    if (item.isTool() && !this.isCreative()) {
                                        if (item.useOn(target) && item.getDamage() >= item.getMaxDurability()) {
                                            this.inventory.setItemInHand(Item.get(0));
                                        } else {
                                            if (this.inventory.getItemInHand().getId() == item.getId() || item.getId() == 0) {
                                                this.inventory.setItemInHand(item);
                                            } else {
                                                server.getLogger().debug("Tried to set item " + item.getId() + " but " + this.username + " had item " + this.inventory.getItemInHand().getId() + " in their hand slot");
                                            }
                                        }
                                    }
                                    return;
                                default:
                                    break;
                            }

                            break;
                        case InventoryTransactionPacket.TYPE_RELEASE_ITEM:
                            if (this.isSpectator()) {
                                this.sendAllInventories();
                                break packetswitch;
                            }
                            ReleaseItemData releaseItemData = (ReleaseItemData) transactionPacket.transactionData;

                            try {
                                type = releaseItemData.actionType;
                                switch (type) {
                                    case InventoryTransactionPacket.RELEASE_ITEM_ACTION_RELEASE:
                                        if (this.isUsingItem()) {
                                            item = this.inventory.getItemInHand();
                                            int ticksUsed = this.server.getTick() - this.startAction;
                                            if (!item.onRelease(this, ticksUsed)) {
                                                this.inventory.sendContents(this);
                                            }
                                            this.setUsingItem(false);
                                        } else {
                                            this.inventory.sendContents(this);
                                        }
                                        return;
                                    case InventoryTransactionPacket.RELEASE_ITEM_ACTION_CONSUME:
                                        if (this.protocol >= 388) break; // Usage of potions on 1.13 and later is handled at ItemPotion#onUse
                                        Item itemInHand = this.inventory.getItemInHand();
                                        PlayerItemConsumeEvent consumeEvent = new PlayerItemConsumeEvent(this, itemInHand);

                                        if (itemInHand.getId() == Item.POTION) {
                                            this.server.getPluginManager().callEvent(consumeEvent);
                                            if (consumeEvent.isCancelled()) {
                                                this.inventory.sendContents(this);
                                                break;
                                            }
                                            Potion potion = Potion.getPotion(itemInHand.getDamage());

                                            if (this.gamemode == SURVIVAL || this.gamemode == ADVENTURE) {
                                                this.getInventory().decreaseCount(this.getInventory().getHeldItemIndex());
                                                this.inventory.addItem(new ItemGlassBottle());
                                            }

                                            if (potion != null) {
                                                potion.applyPotion(this);
                                            }
                                        } else { // Food
                                            this.server.getPluginManager().callEvent(consumeEvent);
                                            if (consumeEvent.isCancelled()) {
                                                this.inventory.sendContents(this);
                                                break;
                                            }

                                            Food food = Food.getByRelative(itemInHand);
                                            if (food != null && food.eatenBy(this)) {
                                                this.getInventory().decreaseCount(this.getInventory().getHeldItemIndex());
                                            }
                                        }
                                        return;
                                    default:
                                        break;
                                }
                            } finally {
                                this.setUsingItem(false);
                            }
                            break;
                        default:
                            this.inventory.sendContents(this);
                            break;
                    }
                    break;
                case ProtocolInfo.PLAYER_HOTBAR_PACKET:
                    PlayerHotbarPacket hotbarPacket = (PlayerHotbarPacket) packet;

                    if (hotbarPacket.windowId != ContainerIds.INVENTORY) {
                        return;
                    }

                    this.inventory.equipItem(hotbarPacket.selectedHotbarSlot);
                    break;
                case ProtocolInfo.SERVER_SETTINGS_REQUEST_PACKET:
                    PlayerServerSettingsRequestEvent settingsRequestEvent = new PlayerServerSettingsRequestEvent(this, new HashMap<>(this.serverSettings));
                    this.getServer().getPluginManager().callEvent(settingsRequestEvent);

                    if (!settingsRequestEvent.isCancelled()) {
                        settingsRequestEvent.getSettings().forEach((id, window) -> {
                            ServerSettingsResponsePacket re = new ServerSettingsResponsePacket();
                            re.formId = id;
                            re.data = window.getJSONData();
                            this.dataPacket(re);
                        });
                    }
                    break;
                case ProtocolInfo.SET_LOCAL_PLAYER_AS_INITIALIZED_PACKET:
                    if (this.locallyInitialized || this.protocol < 274) {
                        return;
                    }

                    this.locallyInitialized = true;
                    this.doFirstSpawn();
                    break;
                case ProtocolInfo.RESPAWN_PACKET:
                    if (this.isAlive() || this.protocol < 388) {
                        break;
                    }

                    RespawnPacket respawnPacket = (RespawnPacket) packet;
                    if (respawnPacket.respawnState == RespawnPacket.STATE_CLIENT_READY_TO_SPAWN) {
                        RespawnPacket respawn1 = new RespawnPacket();
                        respawn1.x = (float) this.getX();
                        respawn1.y = (float) this.getY();
                        respawn1.z = (float) this.getZ();
                        respawn1.respawnState = RespawnPacket.STATE_READY_TO_SPAWN;
                        this.dataPacket(respawn1);
                    }
                    break;
                case ProtocolInfo.BOOK_EDIT_PACKET:
                    BookEditPacket bookEditPacket = (BookEditPacket) packet;
                    Item oldBook = this.inventory.getItem(bookEditPacket.inventorySlot);
                    if (oldBook.getId() != Item.BOOK_AND_QUILL) {
                        return;
                    }

                    if (bookEditPacket.text.length() > 256) {
                        return;
                    }

                    Item newBook = oldBook.clone();
                    boolean success;
                    switch (bookEditPacket.action) {
                        case REPLACE_PAGE:
                            success = ((ItemBookAndQuill) newBook).setPageText(bookEditPacket.pageNumber, bookEditPacket.text);
                            break;
                        case ADD_PAGE:
                            success = ((ItemBookAndQuill) newBook).insertPage(bookEditPacket.pageNumber, bookEditPacket.text);
                            break;
                        case DELETE_PAGE:
                            success = ((ItemBookAndQuill) newBook).deletePage(bookEditPacket.pageNumber);
                            break;
                        case SWAP_PAGES:
                            success = ((ItemBookAndQuill) newBook).swapPages(bookEditPacket.pageNumber, bookEditPacket.secondaryPageNumber);
                            break;
                        case SIGN_BOOK:
                            newBook = Item.get(Item.WRITTEN_BOOK, 0, 1, oldBook.getCompoundTag());
                            success = ((ItemBookWritten) newBook).signBook(bookEditPacket.title, bookEditPacket.author, bookEditPacket.xuid, ItemBookWritten.GENERATION_ORIGINAL);
                            break;
                        default:
                            return;
                    }

                    if (success) {
                        PlayerEditBookEvent editBookEvent = new PlayerEditBookEvent(this, oldBook, newBook, bookEditPacket.action);
                        this.server.getPluginManager().callEvent(editBookEvent);
                        if (!editBookEvent.isCancelled()) {
                            this.inventory.setItem(bookEditPacket.inventorySlot, editBookEvent.getNewBook());
                        }
                    }
                    break;
                case ProtocolInfo.PACKET_VIOLATION_WARNING_PACKET:
                    PacketViolationWarningPacket PVWpk = (PacketViolationWarningPacket) packet;
                    this.getServer().getLogger().info("PacketViolationWarningPacket from " + this.username + ": " + PVWpk.toString());
                    break;
                case ProtocolInfo.EMOTE_PACKET:
                    EmotePacket emotePacket = (EmotePacket) packet;
                    this.emote(emotePacket);
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * Sends a chat message as this player
     * @param message message to send
     * @return successful
     */
    public boolean chat(String message) {
        this.resetCraftingGridType();
        this.craftingType = CRAFTING_SMALL;

        if (this.removeFormat) {
            message = TextFormat.clean(message, true);
        }

        for (String msg : message.split("\n")) {
            if (!msg.trim().isEmpty() && msg.length() <= 255) {
                PlayerChatEvent chatEvent = new PlayerChatEvent(this, msg);
                this.server.getPluginManager().callEvent(chatEvent);
                if (!chatEvent.isCancelled()) {
                    this.server.broadcastMessage(this.getServer().getLanguage().translateString(chatEvent.getFormat(), new String[]{chatEvent.getPlayer().displayName, chatEvent.getMessage()}), chatEvent.getRecipients());
                }
            }
        }

        return true;
    }

    public void emote(EmotePacket emote) {
        for (Player player : this.getViewers().values()) {
            if (player.protocol >= ProtocolInfo.v1_16_0) {
                player.dataPacket(emote);
            }
        }
    }

    public boolean kick() {
        return this.kick("");
    }

    public boolean kick(String reason, boolean isAdmin) {
        return this.kick(PlayerKickEvent.Reason.UNKNOWN, reason, isAdmin);
    }

    public boolean kick(String reason) {
        return kick(PlayerKickEvent.Reason.UNKNOWN, reason);
    }

    public boolean kick(PlayerKickEvent.Reason reason) {
        return this.kick(reason, true);
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString) {
        return this.kick(reason, reasonString, true);
    }

    public boolean kick(PlayerKickEvent.Reason reason, boolean isAdmin) {
        return this.kick(reason, reason.toString(), isAdmin);
    }

    public boolean kick(PlayerKickEvent.Reason reason, String reasonString, boolean isAdmin) {
        PlayerKickEvent ev;
        this.server.getPluginManager().callEvent(ev = new PlayerKickEvent(this, reason, this.getLeaveMessage()));
        if (!ev.isCancelled()) {
            String message;
            if (isAdmin) {
                if (!this.isBanned()) {
                    message = "Kicked!" + (!reasonString.isEmpty() ? " Reason: " + reasonString : "");
                } else {
                    message = reasonString;
                }
            } else {
                if (reasonString.isEmpty()) {
                    message = "disconnectionScreen.noReason";
                } else {
                    message = reasonString;
                }
            }

            this.close(ev.getQuitMessage(), message);

            return true;
        }

        return false;
    }

    public void setViewDistance(int distance) {
        this.chunkRadius = distance;

        ChunkRadiusUpdatedPacket pk = new ChunkRadiusUpdatedPacket();
        pk.radius = distance;

        this.dataPacket(pk);
    }

    public int getViewDistance() {
        return this.chunkRadius;
    }

    @Override
    public void sendMessage(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_RAW;
        pk.message = this.server.getLanguage().translateString(message);
        //pk.setChannel(Network.CHANNEL_TEXT);
        this.dataPacket(pk);
    }

    @Override
    public void sendMessage(TextContainer message) {
        if (message instanceof TranslationContainer) {
            this.sendTranslation(message.getText(), ((TranslationContainer) message).getParameters());
            return;
        }
        this.sendMessage(message.getText());
    }

    public void sendTranslation(String message) {
        this.sendTranslation(message, new String[0]);
    }

    public void sendTranslation(String message, String[] parameters) {
        TextPacket pk = new TextPacket();
        if (!this.server.isLanguageForced()) {
            pk.type = TextPacket.TYPE_TRANSLATION;
            pk.message = this.server.getLanguage().translateString(message, parameters, "nukkit.");
            for (int i = 0; i < parameters.length; i++) {
                parameters[i] = this.server.getLanguage().translateString(parameters[i], parameters, "nukkit.");
            }
            pk.parameters = parameters;
        } else {
            pk.type = TextPacket.TYPE_RAW;
            pk.message = this.server.getLanguage().translateString(message, parameters);
        }
        //pk.setChannel(Network.CHANNEL_TEXT);
        this.dataPacket(pk);
    }

    public void sendChat(String message) {
        this.sendChat("", message);
    }

    public void sendChat(String source, String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_CHAT;
        pk.source = source;
        pk.message = this.server.getLanguage().translateString(message);
        //pk.setChannel(Network.CHANNEL_TEXT);
        this.dataPacket(pk);
    }

    public void sendPopup(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_POPUP;
        pk.message = message;
        //pk.setChannel(Network.CHANNEL_TEXT);
        this.dataPacket(pk);
    }

    public void sendPopup(String message, String subtitle) {
        this.sendPopup(message);
    }

    public void sendTip(String message) {
        TextPacket pk = new TextPacket();
        pk.type = TextPacket.TYPE_TIP;
        pk.message = message;
        //pk.setChannel(Network.CHANNEL_TEXT);
        this.dataPacket(pk);
    }

    public void clearTitle() {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_CLEAR;
        this.dataPacket(pk);
    }

    /**
     * Resets both title animation times and subtitle for the next shown title
     */
    public void resetTitleSettings() {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_RESET;
        this.dataPacket(pk);
    }

    public void setSubtitle(String subtitle) {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_SUBTITLE;
        pk.text = subtitle;
        this.dataPacket(pk);
    }

    public void setTitleAnimationTimes(int fadein, int duration, int fadeout) {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_ANIMATION_TIMES;
        pk.fadeInTime = fadein;
        pk.stayTime = duration;
        pk.fadeOutTime = fadeout;
        this.dataPacket(pk);
    }


    private void setTitle(String text) {
        SetTitlePacket packet = new SetTitlePacket();
        packet.text = text;
        packet.type = SetTitlePacket.TYPE_TITLE;
        this.dataPacket(packet);
    }

    public void sendTitle(String title) {
        this.sendTitle(title, null, 20, 20, 5);
    }

    public void sendTitle(String title, String subtitle) {
        this.sendTitle(title, subtitle, 20, 20, 5);
    }

    public void sendTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        this.setTitleAnimationTimes(fadeIn, stay, fadeOut);
        if (!Strings.isNullOrEmpty(subtitle)) {
            this.setSubtitle(subtitle);
        }
        // Title won't send if an empty string is used
        this.setTitle(Strings.isNullOrEmpty(title) ? " " : title);
    }

    public void sendActionBar(String title) {
        this.sendActionBar(title, 1, 0, 1);
    }

    public void sendActionBar(String title, int fadein, int duration, int fadeout) {
        SetTitlePacket pk = new SetTitlePacket();
        pk.type = SetTitlePacket.TYPE_ACTION_BAR;
        pk.text = title;
        pk.fadeInTime = fadein;
        pk.stayTime = duration;
        pk.fadeOutTime = fadeout;
        this.dataPacket(pk);
    }

    @Override
    public void close() {
        this.close("");
    }

    public void close(String message) {
        this.close(message, "generic");
    }

    public void close(String message, String reason) {
        this.close(message, reason, true);
    }

    public void close(String message, String reason, boolean notify) {
        this.close(new TextContainer(message), reason, notify);
    }

    public void close(TextContainer message) {
        this.close(message, "generic");
    }

    public void close(TextContainer message, String reason) {
        this.close(message, reason, true);
    }

    public void close(TextContainer message, String reason, boolean notify) {
        if (this.connected && !this.closed) {
            if (notify && !reason.isEmpty()) {
                DisconnectPacket pk = new DisconnectPacket();
                pk.message = reason;
                this.directDataPacket(pk);
            }

            this.connected = false;
            PlayerQuitEvent ev = null;
            if (this.username != null && !this.username.isEmpty()) {
                this.server.getPluginManager().callEvent(ev = new PlayerQuitEvent(this, message, true, reason));
                if (this.loggedIn && ev.getAutoSave()) {
                    this.save();
                }
                if (this.fishing != null) {
                    this.stopFishing(false);
                }
            }

            for (Player player : new ArrayList<>(this.server.playerList.values())) {
                if (!player.canSee(this)) {
                    player.showPlayer(this);
                }
            }

            this.hiddenPlayers.clear();

            this.removeAllWindows(true);

            for (long index : new ArrayList<>(this.usedChunks.keySet())) {
                int chunkX = Level.getHashX(index);
                int chunkZ = Level.getHashZ(index);
                this.level.unregisterChunkLoader(this, chunkX, chunkZ);
                this.usedChunks.remove(index);

                for (Entity entity : level.getChunkEntities(chunkX, chunkZ).values()) {
                    if (entity != this) {
                        entity.getViewers().remove(loaderId);
                    }
                }
            }

            super.close();

            this.interfaz.close(this, notify ? reason : "");

            if (this.loggedIn) {
                this.server.removeOnlinePlayer(this);
            }

            this.loggedIn = false;

            if (ev != null && !Objects.equals(this.username, "") && this.spawned && !Objects.equals(ev.getQuitMessage().toString(), "")) {
                this.server.broadcastMessage(ev.getQuitMessage());
            }

            this.server.getPluginManager().unsubscribeFromPermission(Server.BROADCAST_CHANNEL_USERS, this);
            this.spawned = false;
            this.server.getLogger().info(this.getServer().getLanguage().translateString("nukkit.player.logOut",
                    TextFormat.AQUA + (this.username == null ? "" : this.username) + TextFormat.WHITE,
                    this.getAddress(),
                    String.valueOf(this.getPort()),
                    this.getServer().getLanguage().translateString(reason)));
            this.windows.clear();
            this.usedChunks.clear();
            this.loadQueue.clear();
            this.hasSpawned.clear();
            this.spawnPosition = null;

            if (this.riding instanceof EntityRideable) {
                this.riding.passengers.remove(this);
            }

            this.riding = null;
        }

        if (this.perm != null) {
            this.perm.clearPermissions();
            this.perm = null;
        }

        this.inventory = null;
        this.chunk = null;

        this.server.removePlayer(this);
    }

    public void save() {
        this.save(false);
    }

    public void save(boolean async) {
        if (this.closed) {
            throw new IllegalStateException("Tried to save closed player");
        }

        super.saveNBT();

        if (this.level != null) {
            this.namedTag.putString("Level", this.level.getFolderName());
            if (this.spawnPosition != null && this.spawnPosition.getLevel() != null) {
                this.namedTag.putString("SpawnLevel", this.spawnPosition.getLevel().getFolderName());
                this.namedTag.putInt("SpawnX", (int) this.spawnPosition.x);
                this.namedTag.putInt("SpawnY", (int) this.spawnPosition.y);
                this.namedTag.putInt("SpawnZ", (int) this.spawnPosition.z);
            }

            CompoundTag achievements = new CompoundTag();
            for (String achievement : this.achievements) {
                achievements.putByte(achievement, 1);
            }

            this.namedTag.putCompound("Achievements", achievements);

            this.namedTag.putInt("playerGameType", this.gamemode);
            this.namedTag.putLong("lastPlayed", System.currentTimeMillis() / 1000);

            this.namedTag.putString("lastIP", this.getAddress());

            this.namedTag.putInt("EXP", this.exp);
            this.namedTag.putInt("expLevel", this.expLevel);

            this.namedTag.putInt("foodLevel", this.foodData.getLevel());
            this.namedTag.putFloat("foodSaturationLevel", this.foodData.getFoodSaturationLevel());

            if (!this.username.isEmpty() && this.namedTag != null) {
                if (this.server.savePlayerDataByUuid) {
                    this.server.saveOfflinePlayerData(this.uuid, this.namedTag, async);
                } else {
                    this.server.saveOfflinePlayerData(this.username, this.namedTag, async);
                }
            }
        }
    }

    public String getName() {
        return this.username;
    }

    @Override
    public void kill() {
        if (!this.spawned) {
            return;
        }

        boolean showMessages = this.level.getGameRules().getBoolean(GameRule.SHOW_DEATH_MESSAGE);
        String message = "";
        List<String> params = new ArrayList<>();
        EntityDamageEvent cause = this.getLastDamageCause();

        if (showMessages) {
            params.add(this.displayName);

            switch (cause == null ? DamageCause.CUSTOM : cause.getCause()) {
                case ENTITY_ATTACK:
                    if (cause instanceof EntityDamageByEntityEvent) {
                        Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                        killer = e;
                        if (e instanceof Player) {
                            message = "death.attack.player";
                            params.add(((Player) e).displayName);
                            break;
                        } else if (e instanceof EntityLiving) {
                            message = "death.attack.mob";
                            params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                            break;
                        } else {
                            params.add("Unknown");
                        }
                    }
                    break;
                case PROJECTILE:
                    if (cause instanceof EntityDamageByEntityEvent) {
                        Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                        killer = e;
                        if (e instanceof Player) {
                            message = "death.attack.arrow";
                            params.add(((Player) e).displayName);
                        } else if (e instanceof EntityLiving) {
                            message = "death.attack.arrow";
                            params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                            break;
                        } else {
                            params.add("Unknown");
                        }
                    }
                    break;
                case VOID:
                    message = "death.attack.outOfWorld";
                    break;
                case FALL:
                    if (cause.getFinalDamage() > 2) {
                        message = "death.fell.accident.generic";
                        break;
                    }
                    message = "death.attack.fall";
                    break;

                case SUFFOCATION:
                    message = "death.attack.inWall";
                    break;

                case LAVA:
                    message = "death.attack.lava";
                    break;

                case MAGMA:
                    message = "death.attack.magma";
                    break;

                case FIRE:
                    message = "death.attack.onFire";
                    break;

                case FIRE_TICK:
                    message = "death.attack.inFire";
                    break;

                case DROWNING:
                    message = "death.attack.drown";
                    break;

                case CONTACT:
                    if (cause instanceof EntityDamageByBlockEvent) {
                        if (((EntityDamageByBlockEvent) cause).getDamager().getId() == Block.CACTUS) {
                            message = "death.attack.cactus";
                        }
                    }
                    break;

                case BLOCK_EXPLOSION:
                case ENTITY_EXPLOSION:
                    if (cause instanceof EntityDamageByEntityEvent) {
                        Entity e = ((EntityDamageByEntityEvent) cause).getDamager();
                        killer = e;
                        if (e instanceof Player) {
                            message = "death.attack.explosion.player";
                            params.add(((Player) e).displayName);
                        } else if (e instanceof EntityLiving) {
                            message = "death.attack.explosion.player";
                            params.add(!Objects.equals(e.getNameTag(), "") ? e.getNameTag() : e.getName());
                            break;
                        } else {
                            message = "death.attack.explosion";
                        }
                    } else {
                        message = "death.attack.explosion";
                    }
                    break;
                case MAGIC:
                    message = "death.attack.magic";
                    break;
                case LIGHTNING:
                    message = "death.attack.lightningBolt";
                    break;
                case HUNGER:
                    message = "death.attack.starve";
                    break;
                default:
                    message = "death.attack.generic";
                    break;
            }
        }

        PlayerDeathEvent ev = new PlayerDeathEvent(this, this.getDrops(), new TranslationContainer(message, params.toArray(new String[0])), this.expLevel);
        ev.setKeepInventory(this.level.gameRules.getBoolean(GameRule.KEEP_INVENTORY));
        ev.setKeepExperience(ev.getKeepInventory()); // Same as above
        this.server.getPluginManager().callEvent(ev);

        if (!ev.isCancelled()) {
            if (this.fishing != null) {
                this.stopFishing(false);
            }

            this.extinguish();
            this.removeAllEffects();
            this.health = 0;
            this.scheduleUpdate();
            this.ticksSinceLastRest = 0;

            if (!ev.getKeepInventory() && this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                for (Item item : ev.getDrops()) {
                    this.level.dropItem(this, item, null, true, 40);
                }

                if (this.inventory != null) {
                    this.inventory.clearAll();
                }

                // Offhand inventory is already cleared in inventory.clearAll()

                if (this.playerUIInventory != null) {
                    this.playerUIInventory.clearAll();
                }
            }

            if (!ev.getKeepExperience() && this.level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
                if (this.isSurvival() || this.isAdventure()) {
                    int exp = ev.getExperience() * 7;
                    if (exp > 100) exp = 100;
                    this.getLevel().dropExpOrb(this, exp);
                }
                this.setExperience(0, 0);
            }

            if (showMessages && !ev.getDeathMessage().toString().isEmpty()) {
                this.server.broadcast(ev.getDeathMessage(), Server.BROADCAST_CHANNEL_USERS);
            }

            RespawnPacket pk = new RespawnPacket();
            Position pos = this.getSpawn();
            pk.x = (float) pos.x;
            pk.y = (float) pos.y;
            pk.z = (float) pos.z;
            pk.respawnState = RespawnPacket.STATE_SEARCHING_FOR_SPAWN;

            this.teleport(pos, null);

            this.dataPacket(pk);
        }
    }

    @Override
    public void setHealth(float health) {
        if (health < 1) {
            health = 0;
        }

        super.setHealth(health);

        // HACK: solve the client-side absorption bug
        if (this.spawned) {
            UpdateAttributesPacket pk = new UpdateAttributesPacket();
            pk.entries = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getAbsorption() % 2 != 0 ? this.getMaxHealth() + 1 : this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0)};
            pk.entityId = this.id;
            this.dataPacket(pk);
        }
    }

    @Override
    public void setMaxHealth(int maxHealth) {
        super.setMaxHealth(maxHealth);

        if (this.spawned) {
            UpdateAttributesPacket pk = new UpdateAttributesPacket();
            pk.entries = new Attribute[]{Attribute.getAttribute(Attribute.MAX_HEALTH).setMaxValue(this.getAbsorption() % 2 != 0 ? this.getMaxHealth() + 1 : this.getMaxHealth()).setValue(health > 0 ? (health < getMaxHealth() ? health : getMaxHealth()) : 0)};
            pk.entityId = this.id;
            this.dataPacket(pk);
        }
    }

    public int getExperience() {
        return this.exp;
    }

    public int getExperienceLevel() {
        return this.expLevel;
    }

    public void addExperience(int add) {
        if (add == 0) return;
        int added = this.exp + add;
        int level = this.expLevel;
        int most = calculateRequireExperience(level);
        while (added >= most) {
            added -= most;
            level++;
            most = calculateRequireExperience(level);
        }
        this.setExperience(added, level);
    }

    public static int calculateRequireExperience(int level) {
        if (level >= 30) {
            return 112 + (level - 30) * 9;
        } else if (level >= 15) {
            return 37 + (level - 15) * 5;
        } else {
            return 7 + (level << 1);
        }
    }

    public void setExperience(int exp) {
        setExperience(exp, this.expLevel);
    }

    public void setExperience(int exp, int level) {
        this.exp = exp;
        this.expLevel = level;

        this.sendExperienceLevel(level);
        this.sendExperience(exp);
    }

    public void sendExperience() {
        sendExperience(this.exp);
    }

    public void sendExperience(int exp) {
        if (this.spawned) {
            this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE).setValue(Math.max(0f, Math.min(1f, ((float) exp) / calculateRequireExperience(this.expLevel)))));
        }
    }

    public void sendExperienceLevel() {
        sendExperienceLevel(this.expLevel);
    }

    public void sendExperienceLevel(int level) {
        if (this.spawned) {
            this.setAttribute(Attribute.getAttribute(Attribute.EXPERIENCE_LEVEL).setValue(level));
        }
    }

    public void setAttribute(Attribute attribute) {
        UpdateAttributesPacket pk = new UpdateAttributesPacket();
        pk.entries = new Attribute[]{attribute};
        pk.entityId = this.id;
        this.dataPacket(pk);
    }

    @Override
    public void setMovementSpeed(float speed) {
        setMovementSpeed(speed, true);
    }

    public void setMovementSpeed(float speed, boolean send) {
        super.setMovementSpeed(speed);
        if (this.spawned && send) {
            this.setAttribute(Attribute.getAttribute(Attribute.MOVEMENT_SPEED).setValue(speed));
        }
    }

    public Entity getKiller() {
        return killer;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (!this.isAlive()) {
            return false;
        }

        if (this.isSpectator() || (this.isCreative() && source.getCause() != DamageCause.SUICIDE)) {
            source.setCancelled();
            return false;
        } else if (this.adventureSettings.get(Type.ALLOW_FLIGHT) && source.getCause() == DamageCause.FALL) {
            source.setCancelled();
            return false;
        } else if (source.getCause() == DamageCause.FALL) {
            Position pos = this.getPosition().floor().add(0.5, -1, 0.5);
            int block = this.getLevel().getBlockIdAt((int) pos.x, (int) pos.y, (int) pos.z);
            if (block == Block.SLIME_BLOCK || block == Block.COBWEB) {
                if (!this.isSneaking()) {
                    source.setCancelled();
                    this.resetFallDistance();
                    return false;
                }
            }
        }

        if (super.attack(source)) {
            if (this.getLastDamageCause() == source && this.spawned) {
                if (source instanceof EntityDamageByEntityEvent) {
                    Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
                    if (damager instanceof Player) {
                        ((Player) damager).foodData.updateFoodExpLevel(0.3);
                    }
                }
                EntityEventPacket pk = new EntityEventPacket();
                pk.eid = this.id;
                pk.event = EntityEventPacket.HURT_ANIMATION;
                this.dataPacket(pk);
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Drops an item on the ground in front of the player. Returns if the item drop was successful.
     *
     * @param item to drop
     * @return bool if the item was dropped or if the item was null
     */
    public boolean dropItem(Item item) {
        if (!this.spawned || !this.isAlive()) {
            return false;
        }

        if (item.isNull()) {
            this.server.getLogger().debug(this.username + " attempted to drop a null item (" + item + ')');
            return true;
        }

        Vector3 motion = this.getDirectionVector().multiply(0.4);

        this.level.dropItem(this.add(0, 1.3, 0), item, motion, 40);

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);
        return true;
    }

    /**
     * Drops an item on the ground in front of the player. Returns the dropped item.
     *
     * @param item to drop
     * @return EntityItem if the item was dropped or null if the item was null
     */
    public EntityItem dropAndGetItem(Item item) {
        if (!this.spawned || !this.isAlive()) {
            return null;
        }

        if (item.isNull()) {
            this.server.getLogger().debug(this.getName() + " attempted to drop a null item (" + item + ')');
            return null;
        }

        Vector3 motion = this.getDirectionVector().multiply(0.4);

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_ACTION, false);

        return this.level.dropAndGetItem(this.add(0, 1.3, 0), item, motion, 40);
    }

    public void sendPosition(Vector3 pos) {
        this.sendPosition(pos, this.yaw);
    }

    public void sendPosition(Vector3 pos, double yaw) {
        this.sendPosition(pos, yaw, this.pitch);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch) {
        this.sendPosition(pos, yaw, pitch, MovePlayerPacket.MODE_NORMAL);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, int mode) {
        this.sendPosition(pos, yaw, pitch, mode, null);
    }

    public void sendPosition(Vector3 pos, double yaw, double pitch, int mode, Player[] targets) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = this.getId();
        pk.x = (float) pos.x;
        pk.y = (float) (pos.y + this.getEyeHeight());
        pk.z = (float) pos.z;
        pk.headYaw = (float) yaw;
        pk.pitch = (float) pitch;
        pk.yaw = (float) yaw;
        pk.mode = mode;
        //pk.setChannel(Network.CHANNEL_MOVEMENT);

        this.ySize = 0;

        if (targets != null) {
            Server.broadcastPacket(targets, pk);
        } else {
            pk.eid = this.id;
            this.directDataPacket(pk);
        }
    }

    public void sendPosition(double x, double y, double z, double yaw, double pitch, int mode, Collection<Player> targets) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = this.getId();
        pk.x = (float) x;
        pk.y = (float) y;
        pk.z = (float) z;
        pk.headYaw = (float) yaw;
        pk.pitch = (float) pitch;
        pk.yaw = (float) yaw;
        pk.mode = mode;

        this.ySize = 0;

        //pk.setChannel(Network.CHANNEL_MOVEMENT);
        Server.broadcastPacket(targets, pk);
    }

    @Override
    protected void checkChunks() {
        if (this.chunk == null || (this.chunk.getX() != ((int) this.x >> 4) || this.chunk.getZ() != ((int) this.z >> 4))) {
            if (this.chunk != null) {
                this.chunk.removeEntity(this);
            }
            this.chunk = this.level.getChunk((int) this.x >> 4, (int) this.z >> 4, true);

            if (!this.justCreated) {
                Map<Integer, Player> newChunk = this.level.getChunkPlayers((int) this.x >> 4, (int) this.z >> 4);
                newChunk.remove(this.loaderId);

                for (Player player : new ArrayList<>(this.hasSpawned.values())) {
                    if (!newChunk.containsKey(player.loaderId)) {
                        this.despawnFrom(player);
                    } else {
                        newChunk.remove(player.loaderId);
                    }
                }

                for (Player player : newChunk.values()) {
                    this.spawnTo(player);
                }
            }

            if (this.chunk == null) {
                return;
            }

            this.chunk.addEntity(this);
        }
    }

    protected boolean checkTeleportPosition() {
        return checkTeleportPosition(false);
    }

    protected boolean checkTeleportPosition(boolean enderPearl) {
        if (this.teleportPosition != null) {
            int chunkX = (int) this.teleportPosition.x >> 4;
            int chunkZ = (int) this.teleportPosition.z >> 4;

            for (int X = -1; X <= 1; ++X) {
                for (int Z = -1; Z <= 1; ++Z) {
                    long index = Level.chunkHash(chunkX + X, chunkZ + Z);
                    if (!this.usedChunks.containsKey(index) || !this.usedChunks.get(index)) {
                        return false;
                    }
                }
            }

            this.spawnToAll();
            if (!enderPearl) {
                this.forceMovement = this.teleportPosition;
            }
            this.teleportPosition = null;
            return true;
        }

        return false;
    }

    protected void sendPlayStatus(int status) {
        sendPlayStatus(status, false);
    }

    protected void sendPlayStatus(int status, boolean immediate) {
        PlayStatusPacket pk = new PlayStatusPacket();
        pk.status = status;

        if (immediate) {
            this.directDataPacket(pk);
        } else {
            this.dataPacket(pk);
        }
    }

    @Override
    public boolean teleport(Location location, TeleportCause cause) {
        if (!this.isOnline()) {
            return false;
        }

        Location from = this.getLocation();
        Location to = location;

        if (cause != null) {
            PlayerTeleportEvent event = new PlayerTeleportEvent(this, from, to, cause);
            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
            to = event.getTo();
        }

        // HACK: solve the client-side teleporting bug (inside into the block)
        if (super.teleport(to.getY() == to.getFloorY() ? to.add(0, 0.00001, 0) : to, null)) { // null to prevent fire of duplicate EntityTeleportEvent
            this.removeAllWindows();
            this.formOpen = false;

            this.teleportPosition = this;
            if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
                this.forceMovement = this.teleportPosition;
            }
            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_TELEPORT);

            this.checkTeleportPosition(cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL);

            this.resetFallDistance();
            this.nextChunkOrderRun = 0;
            this.newPosition = null;

            this.stopFishing(false);

            // DummyBossBar
            this.dummyBossBars.values().forEach(DummyBossBar::reshow);
            // Weather
            this.getLevel().sendWeather(this);
            // Update time
            this.getLevel().sendTime(this);
            return true;
        }

        return false;
    }

    protected void forceSendEmptyChunks() {
        int chunkPositionX = this.getFloorX() >> 4;
        int chunkPositionZ = this.getFloorZ() >> 4;
        for (int x = -chunkRadius; x < chunkRadius; x++) {
            for (int z = -chunkRadius; z < chunkRadius; z++) {
                LevelChunkPacket chunk = new LevelChunkPacket();
                chunk.chunkX = chunkPositionX + x;
                chunk.chunkZ = chunkPositionZ + z;
                chunk.data = new byte[0];
                //chunk.setChannel(Network.CHANNEL_WORLD_CHUNKS);
                this.dataPacket(chunk);
            }
        }
    }

    public void teleportImmediate(Location location) {
        this.teleportImmediate(location, TeleportCause.PLUGIN);
    }

    public void teleportImmediate(Location location, TeleportCause cause) {
        Location from = this.getLocation();
        if (super.teleport(location.add(0, 0.00001, 0), cause)) {

            for (Inventory window : new ArrayList<>(this.windows.keySet())) {
                if (window == this.inventory) {
                    continue;
                }
                this.removeWindow(window);
            }

            if (from.getLevel().getId() != location.getLevel().getId()) { // Different level, update compass position
                SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
                pk.spawnType = SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
                Position spawn = location.getLevel().getSpawnLocation();
                pk.x = spawn.getFloorX();
                pk.y = spawn.getFloorY();
                pk.z = spawn.getFloorZ();
                this.dataPacket(pk);
            }

            this.forceMovement = this;
            this.sendPosition(this, this.yaw, this.pitch, MovePlayerPacket.MODE_RESET);

            this.resetFallDistance();
            this.orderChunks();
            this.nextChunkOrderRun = 0;
            this.newPosition = null;

            // Weather
            this.getLevel().sendWeather(this);
            // Update time
            this.getLevel().sendTime(this);
        }
    }

    /**
     * Shows a new FormWindow to the player
     * You can find out FormWindow result by listening to PlayerFormRespondedEvent
     *
     * @param window to show
     * @return form id to use in {@link PlayerFormRespondedEvent}
     */
    public int showFormWindow(FormWindow window) {
        return showFormWindow(window, this.formWindowCount++);
    }

    /**
     * Shows a new FormWindow to the player
     * You can find out FormWindow result by listening to PlayerFormRespondedEvent
     *
     * @param window to show
     * @param id form id
     * @return form id to use in {@link PlayerFormRespondedEvent}
     */
    public int showFormWindow(FormWindow window, int id) {
        if (formOpen) return 0;
        ModalFormRequestPacket packet = new ModalFormRequestPacket();
        packet.formId = id;
        packet.data = window.getJSONData();
        this.formWindows.put(packet.formId, window);
        this.dataPacket(packet);
        this.formOpen = true;
        return id;
    }

    /**
     * Shows a new setting page in game settings
     * You can find out settings result by listening to PlayerFormRespondedEvent
     *
     * @param window to show on settings page
     * @return form id to use in {@link PlayerFormRespondedEvent}
     */
    public int addServerSettings(FormWindow window) {
        int id = this.formWindowCount++;

        this.serverSettings.put(id, window);
        return id;
    }

    /**
     * Creates and sends a BossBar to the player
     *
     * @param text   The BossBar message
     * @param length The BossBar percentage
     * @return bossBarId  The BossBar ID, you should store it if you want to remove or update the BossBar later
     */
    public long createBossBar(String text, int length) {
        return this.createBossBar(new DummyBossBar.Builder(this).text(text).length(length).build());
    }

    /**
     * Creates and sends a BossBar to the player
     *
     * @param dummyBossBar DummyBossBar Object (Instantiate it by the Class Builder)
     * @return bossBarId  The BossBar ID, you should store it if you want to remove or update the BossBar later
     * @see DummyBossBar.Builder
     */
    public long createBossBar(DummyBossBar dummyBossBar) {
        this.dummyBossBars.put(dummyBossBar.getBossBarId(), dummyBossBar);
        dummyBossBar.create();
        return dummyBossBar.getBossBarId();
    }

    /**
     * Get a DummyBossBar object
     *
     * @param bossBarId The BossBar ID
     * @return DummyBossBar object
     * @see DummyBossBar#setText(String) Set BossBar text
     * @see DummyBossBar#setLength(float) Set BossBar length
     * @see DummyBossBar#setColor(BlockColor) Set BossBar color
     */
    public DummyBossBar getDummyBossBar(long bossBarId) {
        return this.dummyBossBars.getOrDefault(bossBarId, null);
    }

    /**
     * Get all DummyBossBar objects
     *
     * @return DummyBossBars Map
     */
    public Map<Long, DummyBossBar> getDummyBossBars() {
        return dummyBossBars;
    }

    /**
     * Updates a BossBar
     *
     * @param text      The new BossBar message
     * @param length    The new BossBar length
     * @param bossBarId The BossBar ID
     */
    public void updateBossBar(String text, int length, long bossBarId) {
        if (this.dummyBossBars.containsKey(bossBarId)) {
            DummyBossBar bossBar = this.dummyBossBars.get(bossBarId);
            bossBar.setText(text);
            bossBar.setLength(length);
        }
    }

    /**
     * Removes a BossBar
     *
     * @param bossBarId The BossBar ID
     */
    public void removeBossBar(long bossBarId) {
        if (this.dummyBossBars.containsKey(bossBarId)) {
            this.dummyBossBars.get(bossBarId).destroy();
            this.dummyBossBars.remove(bossBarId);
        }
    }

    public int getWindowId(Inventory inventory) {
        if (this.windows.containsKey(inventory)) {
            return this.windows.get(inventory);
        }

        return -1;
    }

    public Inventory getWindowById(int id) {
        return this.windowIndex.get(id);
    }

    public int addWindow(Inventory inventory) {
        return this.addWindow(inventory, null);
    }

    public int addWindow(Inventory inventory, Integer forceId) {
        return addWindow(inventory, forceId, false);
    }

    public int addWindow(Inventory inventory, Integer forceId, boolean isPermanent) {
        if (this.windows.containsKey(inventory)) {
            return this.windows.get(inventory);
        }
        int cnt;
        if (forceId == null) {
            this.windowCnt = cnt = Math.max(4, ++this.windowCnt % 99);
        } else {
            cnt = forceId;
        }
        this.windows.forcePut(inventory, cnt);

        if (isPermanent) {
            this.permanentWindows.add(cnt);
        }

        if (inventory.open(this)) {
            return cnt;
        } else {
            this.removeWindow(inventory);

            return -1;
        }
    }

    public Optional<Inventory> getTopWindow() {
        for (Entry<Inventory, Integer> entry : this.windows.entrySet()) {
            if (!this.permanentWindows.contains(entry.getValue())) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    public void removeWindow(Inventory inventory) {
        inventory.close(this);
        this.windows.remove(inventory);
    }

    public void sendAllInventories() {
        for (Inventory inv : this.windows.keySet()) {
            inv.sendContents(this);

            if (inv instanceof PlayerInventory) {
                ((PlayerInventory) inv).sendArmorContents(this);
            }
        }
    }

    protected void addDefaultWindows() {
        this.addWindow(this.getInventory(), ContainerIds.INVENTORY, true);

        this.playerUIInventory = new PlayerUIInventory(this);
        this.addWindow(this.playerUIInventory, ContainerIds.UI, true);
        this.addWindow(this.offhandInventory, ContainerIds.OFFHAND, true);

        this.craftingGrid = this.playerUIInventory.getCraftingGrid();
        this.addWindow(this.craftingGrid, ContainerIds.NONE);
    }

    public PlayerUIInventory getUIInventory() {
        return playerUIInventory;
    }

    public PlayerCursorInventory getCursorInventory() {
        return this.playerUIInventory.getCursorInventory();
    }

    public CraftingGrid getCraftingGrid() {
        return this.craftingGrid;
    }

    public void setCraftingGrid(CraftingGrid grid) {
        this.craftingGrid = grid;
        this.addWindow(grid, ContainerIds.NONE);
    }

    public void resetCraftingGridType() {
        if (this.craftingGrid != null) {
            Item[] drops = this.inventory.addItem(this.craftingGrid.getContents().values().toArray(new Item[0]));

            if (drops.length > 0) {
                for (Item drop : drops) {
                    this.dropItem(drop);
                }
            }

            drops = this.inventory.addItem(this.getCursorInventory().getItem(0));
            if (drops.length > 0) {
                for (Item drop : drops) {
                    this.dropItem(drop);
                }
            }

            this.playerUIInventory.clearAll();

            if (this.craftingGrid instanceof BigCraftingGrid) {
                this.craftingGrid = this.playerUIInventory.getCraftingGrid();
                this.addWindow(this.craftingGrid, ContainerIds.NONE);
            }

            this.craftingType = CRAFTING_SMALL;
        }
    }

    /**
     * Remove all windows
     */
    public void removeAllWindows() {
        removeAllWindows(false);
    }

    /**
     * Remove all windows
     * @param permanent remove permanent windows
     */
    public void removeAllWindows(boolean permanent) {
        for (Entry<Integer, Inventory> entry : new ArrayList<>(this.windowIndex.entrySet())) {
            if (!permanent && this.permanentWindows.contains(entry.getKey())) {
                continue;
            }

            this.removeWindow(entry.getValue());
        }
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) {
        this.server.getPlayerMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) {
        return this.server.getPlayerMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) {
        this.server.getPlayerMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    @Override
    public void onChunkChanged(FullChunk chunk) {
        this.usedChunks.remove(Level.chunkHash(chunk.getX(), chunk.getZ()));
    }

    @Override
    public void onChunkLoaded(FullChunk chunk) {
    }

    @Override
    public void onChunkPopulated(FullChunk chunk) {
    }

    @Override
    public void onChunkUnloaded(FullChunk chunk) {
    }

    @Override
    public void onBlockChanged(Vector3 block) {
    }

    @Override
    public int getLoaderId() {
        return this.loaderId;
    }

    @Override
    public boolean isLoaderActive() {
        return this.connected;
    }

    /**
     * Get chunk cache from data
     * @param protocol protocol version
     * @param chunkX chunk x
     * @param chunkZ chunk z
     * @param subChunkCount sub chunk count
     * @param payload data
     * @return BatchPacket
     */
    public static BatchPacket getChunkCacheFromData(int protocol, int chunkX, int chunkZ, int subChunkCount, byte[] payload) {
        LevelChunkPacket pk = new LevelChunkPacket();
        pk.chunkX = chunkX;
        pk.chunkZ = chunkZ;
        pk.subChunkCount = subChunkCount;
        pk.data = payload;
        pk.protocol = protocol;
        //pk.setChannel(Network.CHANNEL_WORLD_CHUNKS);
        pk.encode();

        BatchPacket batch = new BatchPacket();
        byte[][] batchPayload = new byte[2][];
        byte[] buf = pk.getBuffer();
        batchPayload[0] = Binary.writeUnsignedVarInt(buf.length);
        batchPayload[1] = buf;
        try {
            if (protocol >= 407) {
                batch.payload = Zlib.deflateRaw(Binary.appendBytes(batchPayload), Server.getInstance().networkCompressionLevel);
            } else {
                batch.payload = Zlib.deflate(Binary.appendBytes(batchPayload), Server.getInstance().networkCompressionLevel);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return batch;
    }

    /**
     * Check whether food is enabled or not
     * @return food enabled
     */
    public boolean isFoodEnabled() {
        return !(this.isCreative() || this.isSpectator()) && this.foodEnabled;
    }

    /**
     * Enable or disable food
     * @param foodEnabled food enabled
     */
    public void setFoodEnabled(boolean foodEnabled) {
        this.foodEnabled = foodEnabled;
    }

    /**
     * Get player's food data
     * @return food data
     */
    public PlayerFood getFoodData() {
        return this.foodData;
    }

    /**
     * Send dimension change
     * @param dimension dimension id
     */
    public void setDimension(int dimension) {
        ChangeDimensionPacket pk1 = new ChangeDimensionPacket();
        pk1.dimension = dimension;
        pk1.x = (float) this.x;
        pk1.y = (float) this.y;
        pk1.z = (float) this.z;
        pk1.respawn = !this.isAlive();
        this.directDataPacket(pk1);

        if (this.protocol >= 313) {
            NetworkChunkPublisherUpdatePacket pk0 = new NetworkChunkPublisherUpdatePacket();
            pk0.position = new BlockVector3((int) this.x, (int) this.y, (int) this.z);
            pk0.radius = viewDistance << 4;
            this.directDataPacket(pk0);
        }
    }

    @Override
    public boolean switchLevel(Level level) {
        Level oldLevel = this.level;
        if (super.switchLevel(level)) {
            //this.setImmobile(true);

            SetSpawnPositionPacket spawnPosition = new SetSpawnPositionPacket();
            spawnPosition.spawnType = SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
            Position spawn = level.getSpawnLocation();
            spawnPosition.x = spawn.getFloorX();
            spawnPosition.y = spawn.getFloorY();
            spawnPosition.z = spawn.getFloorZ();
            this.dataPacket(spawnPosition);

            this.forceSendEmptyChunks();

            // Remove old chunks
            for (long index : new ArrayList<>(this.usedChunks.keySet())) {
                int chunkX = Level.getHashX(index);
                int chunkZ = Level.getHashZ(index);
                this.unloadChunk(chunkX, chunkZ, oldLevel);
            }

            this.usedChunks.clear();
            this.loadQueue.clear();

            level.sendTime(this);
            level.sendWeather(this);

            // Update game rules
            GameRulesChangedPacket packet = new GameRulesChangedPacket();
            packet.gameRules = level.getGameRules();
            this.dataPacket(packet);

            if (this.getServer().dimensionsEnabled && oldLevel.getDimension() != level.getDimension()) {
                this.setDimension(level.getDimension());
            }

            this.ticksSinceLastRest = 0;

            //this.setImmobile(false);
            return true;
        }

        return false;
    }

    /**
     * Enable or disable movement check
     * @param checkMovement movement check enabled
     */
    public void setCheckMovement(boolean checkMovement) {
        this.checkMovement = checkMovement;
    }

    /**
     * @return player movement checks enabled
     */
    public boolean isCheckingMovement() {
        return this.checkMovement;
    }

    /**
     * Set locale
     * @param locale locale
     */
    public synchronized void setLocale(Locale locale) {
        this.locale.set(locale);
    }

    /**
     * Get locale
     * @return locale
     */
    public synchronized Locale getLocale() {
        return this.locale.get();
    }

    @Override
    public void setSprinting(boolean value) {
        if (isSprinting() != value) {
            super.setSprinting(value);
            this.setMovementSpeed(value ? getMovementSpeed() * 1.3f : getMovementSpeed() / 1.3f, false);
        }
    }

    /**
     * Transfer player to other server
     * @param address target server address
     */
    public void transfer(InetSocketAddress address) {
        transfer(address.getAddress().getHostAddress(), address.getPort());
    }

    /**
     * Transfer player to other server
     * @param hostName target server address
     * @param port target server port
     */
    public void transfer(String hostName, int port) {
        TransferPacket pk = new TransferPacket();
        pk.address = hostName;
        pk.port = port;
        this.dataPacket(pk);
    }

    /**
     * Get player's LoginChainData
     * @return login chain data
     */
    public LoginChainData getLoginChainData() {
        return this.loginChainData;
    }

    /**
     * Try to pick up an entity
     * @param entity target
     * @param near near
     * @return success
     */
    public boolean pickupEntity(Entity entity, boolean near) {
        if (!this.spawned || !this.isAlive() || !this.isOnline() || this.isSpectator() || entity.isClosed()) {
            return false;
        }

        if (near) {
            if (entity instanceof EntityArrow && ((EntityArrow) entity).hadCollision) {
                ItemArrow item = new ItemArrow();
                if (!this.isCreative() && !this.inventory.canAddItem(item)) {
                    return false;
                }

                InventoryPickupArrowEvent ev = new InventoryPickupArrowEvent(this.inventory, (EntityArrow) entity);

                int pickupMode = ((EntityArrow) entity).getPickupMode();
                if (pickupMode == EntityArrow.PICKUP_NONE || (pickupMode == EntityArrow.PICKUP_CREATIVE && !this.isCreative())) {
                    ev.setCancelled();
                }

                this.server.getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    return false;
                }

                TakeItemEntityPacket pk = new TakeItemEntityPacket();
                pk.entityId = this.getId();
                pk.target = entity.getId();
                Server.broadcastPacket(entity.getViewers().values(), pk);
                this.dataPacket(pk);

                if (!this.isCreative()) {
                    this.inventory.addItem(item.clone());
                }
                entity.close();
                return true;
            } else if (entity instanceof EntityThrownTrident && ((EntityThrownTrident) entity).hadCollision) {
                Item item = ((EntityThrownTrident) entity).getItem();
                if (!this.isCreative() && !this.inventory.canAddItem(item)) {
                    return false;
                }

                TakeItemEntityPacket pk = new TakeItemEntityPacket();
                pk.entityId = this.getId();
                pk.target = entity.getId();
                Server.broadcastPacket(entity.getViewers().values(), pk);
                this.dataPacket(pk);

                if (!this.isCreative()) {
                    this.inventory.addItem(item.clone());
                }
                entity.close();
                return true;
            } else if (entity instanceof EntityItem) {
                if (((EntityItem) entity).getPickupDelay() <= 0) {
                    Item item = ((EntityItem) entity).getItem();

                    if (item != null) {
                        if (!this.isCreative() && !this.inventory.canAddItem(item)) {
                            return false;
                        }

                        InventoryPickupItemEvent ev;
                        this.server.getPluginManager().callEvent(ev = new InventoryPickupItemEvent(this.inventory, (EntityItem) entity));
                        if (ev.isCancelled()) {
                            return false;
                        }

                        switch (item.getId()) {
                            case Item.WOOD:
                            case Item.WOOD2:
                                this.awardAchievement("mineWood");
                                break;
                            case Item.DIAMOND:
                                this.awardAchievement("diamond");
                                break;
                        }

                        TakeItemEntityPacket pk = new TakeItemEntityPacket();
                        pk.entityId = this.getId();
                        pk.target = entity.getId();
                        Server.broadcastPacket(entity.getViewers().values(), pk);
                        this.dataPacket(pk);

                        this.inventory.addItem(item.clone());
                        entity.close();
                        return true;
                    }
                }
            }
        }

        if (pickedXPOrb < server.getTick() && entity instanceof EntityXPOrb && this.boundingBox.isVectorInside(entity)) {
            EntityXPOrb xpOrb = (EntityXPOrb) entity;
            if (xpOrb.getPickupDelay() <= 0) {
                int exp = xpOrb.getExp();
                entity.close();
                this.getLevel().addSound(new ExperienceOrbSound(this));
                pickedXPOrb = server.getTick();

                ArrayList<Integer> itemsWithMending = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    if (inventory.getArmorItem(i).getEnchantment((short)Enchantment.ID_MENDING) != null) {
                        itemsWithMending.add(inventory.getSize() + i);
                    }
                }
                if (inventory.getItemInHand().getEnchantment((short)Enchantment.ID_MENDING) != null) {
                    itemsWithMending.add(inventory.getHeldItemIndex());
                }
                if (!itemsWithMending.isEmpty()) {
                    int itemToRepair = itemsWithMending.get(Utils.random.nextInt(itemsWithMending.size()));
                    Item toRepair = inventory.getItem(itemToRepair);
                    if (toRepair instanceof ItemTool || toRepair instanceof ItemArmor) {
                        if (toRepair.getDamage() > 0) {
                            int dmg = toRepair.getDamage() - 2;
                            if (dmg < 0) {
                                dmg = 0;
                            }
                            toRepair.setDamage(dmg);
                            inventory.setItem(itemToRepair, toRepair);
                            return true;
                        }
                    }
                }

                this.addExperience(exp);
                return true;
            }
        }

        return false;
    }

    @Override
    public int hashCode() {
        if ((this.hash == 0) || (this.hash == 485)) {
            this.hash = (485 + (getUniqueId() != null ? getUniqueId().hashCode() : 0));
        }

        return this.hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Player)) {
            return false;
        }
        Player other = (Player) obj;
        return Objects.equals(this.getUniqueId(), other.getUniqueId()) && this.getId() == other.getId();
    }

    public boolean isBreakingBlock() {
        return this.breakingBlock != null;
    }

    /**
     * Show a window of a XBOX account's profile
     * @param xuid XUID
     */
    public void showXboxProfile(String xuid) {
        ShowProfilePacket pk = new ShowProfilePacket();
        pk.xuid = xuid;
        this.dataPacket(pk);
    }

    /**
     * Start fishing
     * @param fishingRod fishing rod item
     */
    public void startFishing(Item fishingRod) {
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos")
                        .add(new DoubleTag("", x))
                        .add(new DoubleTag("", y + this.getEyeHeight()))
                        .add(new DoubleTag("", z)))
                .putList(new ListTag<DoubleTag>("Motion")
                        .add(new DoubleTag("", -Math.sin(yaw / 180 + Math.PI) * Math.cos(pitch / 180 * Math.PI)))
                        .add(new DoubleTag("", -Math.sin(pitch / 180 * Math.PI)))
                        .add(new DoubleTag("", Math.cos(yaw / 180 * Math.PI) * Math.cos(pitch / 180 * Math.PI))))
                .putList(new ListTag<FloatTag>("Rotation")
                        .add(new FloatTag("", (float) yaw))
                        .add(new FloatTag("", (float) pitch)));
        double f = 1.1;
        EntityFishingHook fishingHook = new EntityFishingHook(chunk, nbt, this);
        fishingHook.setMotion(new Vector3(-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f, -Math.sin(FastMath.toRadians(pitch)) * f * f,
                Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f));
        ProjectileLaunchEvent ev = new ProjectileLaunchEvent(fishingHook);
        this.getServer().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            fishingHook.close();
        } else {
            fishingHook.spawnToAll();
            this.fishing = fishingHook;
            fishingHook.rod = fishingRod;
        }
    }

    /**
     * Stop fishing
     * @param click clicked or forced
     */
    public void stopFishing(boolean click) {
        if (this.fishing != null && click) {
            fishing.reelLine();
        } else if (this.fishing != null) {
            this.fishing.close();
        }

        this.fishing = null;
    }

    @Override
    public boolean doesTriggerPressurePlate() {
        return this.gamemode != SPECTATOR;
    }

    @Override
    protected void onBlock(Entity entity, boolean animate) {
        super.onBlock(entity, animate);
        if (animate) {
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHIELD_SHAKING, true);
            this.getServer().getScheduler().scheduleTask(null, ()-> {
                if (this.isOnline()) {
                    this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHIELD_SHAKING, false);
                }
            });
        }
    }

    @Override
    public String toString() {
        return "Player(name='" + getName() + "', location=" + super.toString() + ')';
    }
}
