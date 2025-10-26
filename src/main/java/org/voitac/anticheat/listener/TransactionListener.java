package org.voitac.anticheat.listener;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.voitac.anticheat.AntiCheat;
import org.bukkit.entity.Player;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;

// TODO Rewrite
// Very Convoluted Ping Tracker
/**
 * RelMove should prompt a transaction, relmove will be validated when this transaction has been confirmed by the client
 * TransactionOutTime - TransactionInTime = Ping (half it for incoming delay?)
 */
public final class TransactionListener {

    private final HashMap<Player, PlayerPingManager> playerPingManagerHashMap = new HashMap<>();

    private final HashMap<Player, HashMap<Short, TransactionWrapper>> playerTransactionMap = new HashMap<>();

    public TransactionListener() {
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(AntiCheat.getInstance().getPlugin(), ListenerPriority.HIGHEST,
                PacketType.Play.Client.TRANSACTION,
                PacketType.Play.Server.TRANSACTION
        ) {
            @Override
            public void onPacketReceiving(final PacketEvent event) {
                handleInTransaction(event);
            }

            @Override
            public void onPacketSending(final PacketEvent event) {
                handleOutTransaction(event);
            }
        });
    }

    private void handleInTransaction(final PacketEvent transaction) {
        final Player player = transaction.getPlayer();
        final PlayerPingManager playerPingManager = this.playerPingManagerHashMap.computeIfAbsent(player, key -> new PlayerPingManager());

        final TransactionWrapper transactionWrapper = playerPingManager.accept(transaction.getPacket().getShorts().getValues().get(0));

        if(transactionWrapper == null)
            return;

        AntiCheat.getInstance().getDataManager().getPlayerData(player).setPing(transactionWrapper.accept()).setSynced(true);
    }

    private void handleOutTransaction(final PacketEvent transaction) {
        final Player player = transaction.getPlayer();
        final PlayerPingManager playerPingManager = this.playerPingManagerHashMap.computeIfAbsent(player, key -> new PlayerPingManager());
        this.playerTransactionMap.computeIfAbsent(player, key -> new HashMap<>());
        final TransactionWrapper transactionWrapper = this.constructTransaction(player);
        playerPingManager.publish(transactionWrapper);
    }

    public void handleRelMove(final Player player) throws InvocationTargetException {
        this.deliverPreConstructedTransaction(player);
        AntiCheat.getInstance().getDataManager().getPlayerData(player).setSynced(false);
    }

    private TransactionWrapper constructTransaction(final Player player) {
        final PlayerPingManager playerPingManager = this.playerPingManagerHashMap.computeIfAbsent(player, key -> new PlayerPingManager());
        final PacketContainer transaction = new PacketContainer(PacketType.Play.Server.TRANSACTION);
        this.playerPingManagerHashMap.putIfAbsent(player, new PlayerPingManager());
        final short id = this.playerPingManagerHashMap.get(player).getExpectedID();

        transaction.getShorts().write(0, id);
        transaction.getIntegers().write(0, 0);
        transaction.getBooleans().write(0, false);

        return new TransactionWrapper(id, transaction);
    }

    private void deliverPreConstructedTransaction(final Player player) {
        final PlayerPingManager playerPingManager = this.playerPingManagerHashMap.computeIfAbsent(player, key -> new PlayerPingManager());
        this.playerTransactionMap.computeIfAbsent(player, key -> new HashMap<>());
        final TransactionWrapper transactionWrapper = this.constructTransaction(player);
        final PacketContainer trans = transactionWrapper.packetContainer;

        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, trans);
            playerPingManager.publish(transactionWrapper);
            this.playerTransactionMap.get(player).put(transactionWrapper.getUid(), transactionWrapper);
        }catch (final Exception ignored) {
        }
    }

    private static final class TransactionWrapper {
        private final short uid;
        private final PacketContainer packetContainer;
        private final long creationTime;
        public TransactionWrapper(final short uid, final PacketContainer packetContainer) {
            this.uid = uid;
            this.packetContainer = packetContainer;
            this.creationTime = System.currentTimeMillis();
        }
        public short getUid() {
            return uid;
        }

        public PacketContainer getPacketContainer() {
            return packetContainer;
        }

        public long accept() {
            return System.currentTimeMillis() - this.creationTime;
        }
    }

    private static final class PlayerPingManager {
        private short nextID;
        private final HashMap<Short, TransactionWrapper> cachedMap = new HashMap<>();
        public PlayerPingManager() {
            this.nextID = Short.MIN_VALUE;
        }

        public void publish(final TransactionWrapper transactionWrapper) {
            this.cachedMap.put(transactionWrapper.getUid(), transactionWrapper);
        }

        public TransactionWrapper accept(final short id) {
            if(this.contains(id)) {
                final TransactionWrapper transactionWrapper = this.cachedMap.get(id);
                this.cachedMap.remove(id);
                this.nextID = (short) (transactionWrapper.uid + 1);
                return transactionWrapper;
            }
            return null;
        }

        public short getExpectedID() {
            return this.nextID;
        }

        public boolean contains(final short id) {
            return this.cachedMap.containsKey(id);
        }
    }
}
