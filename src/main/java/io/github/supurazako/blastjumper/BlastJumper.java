package io.github.supurazako.blastjumper;

import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


public final class BlastJumper extends JavaPlugin implements Listener {

    @Override
    public void onEnable() {
        // Plugin startup logic
        logger = getLogger();

        getServer().getPluginManager().registerEvents(this, this);

        loadConfiguration();

    }

    private double tntEffectRadius; // 爆発効果の半径
    private double tntEffectPower; // 爆発効果の威力

    private void loadConfiguration() {
        try {
            getConfig().options().copyDefaults(true);
            saveDefaultConfig();
            reloadConfig();
            logger.info("configをロードしました");

            // TNTの爆発効果の設定を読み込む
            tntEffectRadius = getConfig().getDouble("tntEffect.radius");
            tntEffectPower = getConfig().getDouble("tntEffect.power");
        } catch (Exception e) {
            logger.severe("configのロード中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private Logger logger;


    Map<Player, Integer> playerTNTMap = new HashMap<>(); // プレイヤーとTNTエンティティIDを関連付けるマップ
    Map<Integer, TNTPrimed> tntMap = new HashMap<>(); // TNTエンティティIDとTNTエンティティオブジェクトを関連付けるマップ
    Map<Integer, Integer> tntTimers = new HashMap<>(); // tntの自動爆発のためのタイマー管理


    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        try {
            if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                ItemStack item = event.getItem();
                logger.info("右クリックを検知");

                // 右クリックしているのが時計かを確認
                if (item != null && item.getType() == Material.CLOCK) {

                    logger.info("右クリックが時計なのを確認");
                    Player player = event.getPlayer();

                    int playerEntityId = player.getEntityId();

                    // プレイヤーがTNTを持っているか確認
                    if (player.getInventory().contains(Material.TNT)) {
                        if (tntTimers.containsKey(playerEntityId)) {
                            logger.info("TNTがすでに呼び出されている");
                            // 既にTNTが呼び出されている場合の処理
                            int tntEntityId = playerTNTMap.get(player);
                            triggerTNTExplosionEffect(player, tntEntityId);

                        } else {
                            // TNTがまだ呼び出されていない場合
                            logger.info("TNTはまだ呼び出されていない");

                            // TNTを呼び出す処理
                            spawnTNT(player);

                            // TNTを消費する
                            player.getInventory().removeItem(new ItemStack(Material.TNT, 1));
                        }


                    } else {
                        // TNTを持っていない場合
                        logger.info("TNTを持っていない");
                        player.sendMessage("TNTを持っていません");
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("エラーが発生しました: " + e.getMessage());
            e.printStackTrace(); // エラーメッセージの表示
        }
    }


    private void spawnTNT(Player player) {
        try {

            // プレイヤーの場所とプレイヤーの向いてる方向を取得
            World world = player.getWorld();
            Location location = player.getLocation();
            Vector direction = location.getDirection();

            double speed = 3.0; // 速度の値 必要に応じて調整
            int fuseTicks = 20 * 5; // 爆発するまでの時間
            int timerDelay = fuseTicks - 3;

            // normalize()で長さが1のベクトルに正規化、multiply()でそれを乗算
            Vector velocity = direction.normalize().multiply(speed);

            TNTPrimed tnt = (TNTPrimed) world.spawnEntity(location, EntityType.PRIMED_TNT);
            int tntEntityId = tnt.getEntityId(); // TNTエンティティのエンティティIDを取得

            int timerId = Bukkit.getScheduler().scheduleSyncDelayedTask(this, () -> triggerTNTExplosionEffect(player, tntEntityId), timerDelay);

            tntTimers.put(player.getEntityId(), timerId);

            playerTNTMap.put(player, tntEntityId);

            tntMap.put(tntEntityId, tnt);

            tnt.setFuseTicks(fuseTicks);
            tnt.setVelocity(velocity);

        } catch (Exception e) {
            // エラーメッセージを出力
            logger.severe("spawnTNTの処理中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void triggerTNTExplosionEffect(Player player, int tntEntityId) {
        try {
            if (playerTNTMap.containsKey(player)) {

                TNTPrimed tnt = tntMap.get(tntEntityId);

                if (tnt != null) {
                    // TNTの爆発エフェクトとサウンドエフェクトのスポーン処理
                    Location tntLocation = tnt.getLocation();
                    player.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, tntLocation, 1);
                    player.getWorld().playSound(tntLocation, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 1, 1);

                    // TNTのふっとばし処理

                    // エンティティの検索範囲の半径(メートル)
                    double radius = tntEffectRadius;

                    // TNTの力
                    double power = tntEffectPower;

                    // エンティティを検索
                    List<Entity> nearbyEntities = (List<Entity>) tntLocation.getWorld().getNearbyEntities(tntLocation, radius, radius, radius);

                    // TNTエンティティとの相対的な位置と角度に基づいてエンティティに速度を設定
                    for (Entity entity : nearbyEntities) {
                        // TNTエンティティ自体は除外する
                        if (entity.equals(tnt)) {
                            continue;
                        }

                        // エンティティとTNTエンティティの相対的な位置を計算
                        Vector relativePosition = entity.getLocation().subtract(tntLocation).toVector();

                        // エンティティとTNTエンティティの角度を計算
                        double yaw = Math.toRadians(tntLocation.getYaw());
                        double pitch = Math.toRadians(tntLocation.getPitch());

                        // 相対的な位置をTNTエンティティの角度に回転
                        // TNTに向かって正面を向いていれば問題が、回転させないと別の方向に行ってしまう
                        double newX = relativePosition.getX() * Math.cos(yaw) + relativePosition.getZ() * Math.sin(yaw);
                        double newZ = -relativePosition.getX() * Math.sin(yaw) + relativePosition.getZ() * Math.cos(yaw);
                        double newY = relativePosition.getY() * Math.cos(pitch) - newZ * Math.cos(pitch);
                        newZ = relativePosition.getY() * Math.sin(pitch) + newZ * Math.cos(pitch);

                        // 相対的な位置に基づいてエンティティに速度を設定
                        Vector velocity = new Vector(newX, newY, newZ).normalize().multiply(power);
                        entity.setVelocity(velocity);

                    }

                    // TNTの削除
                    tnt.remove();

                    // プレイヤーとTNTの紐づけ解除
                    playerTNTMap.remove(player);
                    tntMap.remove(tntEntityId);
                    tntTimers.remove(player.getEntityId());
                }
            }
        } catch (Exception e) {
            // エラーメッセージの出力
            logger.severe("triggerTNTExplosionEffectの処理中にエラーが発生しました: " + e.getMessage());
            e.printStackTrace();
        }
    }


    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }
}
