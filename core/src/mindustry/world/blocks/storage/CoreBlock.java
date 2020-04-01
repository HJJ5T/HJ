package mindustry.world.blocks.storage;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.*;
import mindustry.annotations.Annotations.*;
import mindustry.content.*;
import mindustry.entities.*;
import mindustry.entities.bullet.*;
import mindustry.entities.traits.*;
import mindustry.entities.type.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.type.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.*;
import mindustry.world.blocks.units.UnitFactory.*;
import mindustry.world.meta.*;
import mindustry.world.modules.*;

import static mindustry.Vars.*;

public class CoreBlock extends StorageBlock{
    public Mech mech = Mechs.starter;

    public final int timerMiner = timers++;
    public final int timerBattery = timers++;

    private Array<Tile> nearby = new Array<>();

    public CoreBlock(String name){
        super(name);

        solid = true;
        update = true;
        hasItems = true;
        flags = EnumSet.of(BlockFlag.core, BlockFlag.producer);
        activeSound = Sounds.respawning;
        activeSoundVolume = 1f;
        layer = Layer.overlay;
        entityType = CoreEntity::new;
    }

    @Remote(called = Loc.server)
    public static void onUnitRespawn(Tile tile, Player player){
        if(player == null || tile.entity == null) return;

        CoreEntity entity = tile.ent();
        Effects.effect(Fx.spawn, entity);
        entity.progress = 0;
        entity.spawnPlayer = player;
        entity.spawnPlayer.onRespawn(tile);
        entity.spawnPlayer.applyImpulse(0, 8f);
        entity.spawnPlayer = null;
    }

    @Override
    public void setStats(){
        super.setStats();

        bars.add("capacity", e ->
            new Bar(
                () -> Core.bundle.format("bar.capacity", ui.formatAmount(((CoreEntity)e).storageCapacity)),
                () -> Pal.items,
                () -> e.items.total() / (float)(((CoreEntity)e).storageCapacity * content.items().count(i -> i.type == ItemType.material))
            ));
    }

    @Override
    public void drawLight(Tile tile){
        renderer.lights.add(tile.drawx(), tile.drawy(), 30f * size, Pal.accent, 0.5f + Mathf.absin(20f, 0.1f));
    }

    @Override
    public boolean acceptItem(Item item, Tile tile, Tile source){
        return tile.entity.items.get(item) < getMaximumAccepted(tile, item);
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        CoreEntity entity = tile.ent();
        return item.type == ItemType.material ? entity.storageCapacity : 0;
    }

    @Override
    public void onProximityUpdate(Tile tile){
        CoreEntity entity = tile.ent();

        for(TileEntity other : state.teams.cores(tile.getTeam())){
            if(other.tile != tile){
                entity.items = other.items;
            }
        }
        state.teams.registerCore(entity);

        entity.storageCapacity = itemCapacity + entity.proximity().sum(e -> isContainer(e) ? e.block().itemCapacity : 0);
        entity.proximity().each(this::isContainer, t -> {
            t.entity.items = entity.items;
            t.<StorageBlockEntity>ent().linkedCore = tile;
        });

        for(TileEntity other : state.teams.cores(tile.getTeam())){
            if(other.tile == tile) continue;
            entity.storageCapacity += other.block.itemCapacity + other.proximity().sum(e -> isContainer(e) ? e.block().itemCapacity : 0);
        }

        if(!world.isGenerating()){
            for(Item item : content.items()){
                entity.items.set(item, Math.min(entity.items.get(item), entity.storageCapacity));
            }
        }

        for(CoreEntity other : state.teams.cores(tile.getTeam())){
            other.storageCapacity = entity.storageCapacity;
        }
    }

    @Override
    public void drawSelect(Tile tile){
        Lines.stroke(1f, Pal.accent);
        Cons<Tile> outline = t -> {
            for(int i = 0; i < 4; i++){
                Point2 p = Geometry.d8edge[i];
                float offset = -Math.max(t.block().size - 1, 0) / 2f * tilesize;
                Draw.rect("block-select", t.drawx() + offset * p.x, t.drawy() + offset * p.y, i * 90);
            }
        };
        if(tile.entity.proximity().contains(e -> isContainer(e) && e.entity.items == tile.entity.items)){
            outline.get(tile);
        }
        tile.entity.proximity().each(e -> isContainer(e) && e.entity.items == tile.entity.items, outline);
        Draw.reset();
    }


    public boolean isContainer(Tile tile){
        return tile.entity instanceof StorageBlockEntity;
    }

    @Override
    public float handleDamage(Tile tile, float amount){
        if(player != null && tile.getTeam() == player.getTeam()){
            Events.fire(Trigger.teamCoreDamage);
        }
        return amount;
    }

    @Override
    public boolean canBreak(Tile tile){
        return false;
    }

    @Override
    public void removed(Tile tile){
        CoreEntity entity = tile.ent();
        int total = tile.entity.proximity().count(e -> e.entity != null && e.entity.items != null && e.entity.items == tile.entity.items);
        float fract = 1f / total / state.teams.cores(tile.getTeam()).size;

        tile.entity.proximity().each(e -> isContainer(e) && e.entity.items == tile.entity.items, t -> {
            StorageBlockEntity ent = (StorageBlockEntity)t.entity;
            ent.linkedCore = null;
            ent.items = new ItemModule();
            for(Item item : content.items()){
                ent.items.set(item, (int)(fract * tile.entity.items.get(item)));
            }
        });

        state.teams.unregisterCore(entity);

        int max = itemCapacity * state.teams.cores(tile.getTeam()).size;
        for(Item item : content.items()){
            tile.entity.items.set(item, Math.min(tile.entity.items.get(item), max));
        }

        for(CoreEntity other : state.teams.cores(tile.getTeam())){
            other.block.onProximityUpdate(other.tile);
        }
    }

    @Override
    public void placed(Tile tile){
        super.placed(tile);
        CoreEntity entity = tile.ent();
        state.teams.registerCore(entity);
    }

    @Override
    public void drawLayer(Tile tile){
        CoreEntity entity = tile.ent();

        if(entity.heat > 0.001f){
            RespawnBlock.drawRespawn(tile, entity.heat, entity.progress, entity.time, entity.spawnPlayer, mech);
        }
    }

    @Override
    public void handleItem(Item item, Tile tile, Tile source){
        if(net.server() || !net.active()){
            super.handleItem(item, tile, source);
            if(state.rules.tutorial){
                Events.fire(new CoreItemDeliverEvent());
            }
        }
    }

    @Override
    public void update(Tile tile){
        CoreEntity entity = tile.ent();

        if(entity.timer.get(timerMiner, 60)){
            for(Item ore : UnitTypes.draug.toMine){
                if(tile.getTeam().miners(ore).size < tile.getTeam().cores().size){
                    BaseUnit unit = UnitTypes.draug.create(tile.getTeam());
                    unit.setSpawner(tile);
                    unit.set(tile.drawx() + Mathf.range(4), tile.drawy() + Mathf.range(4));
                    unit.item().item = ore;
                    unit.add();
                }
            }

            tile.getTeam().draugfactories = indexer.getAllied(tile.getTeam(), BlockFlag.producer).select(t -> t.block == Blocks.draugFactory).asArray().count(t -> t.<UnitFactoryEntity>ent().spawned > 0);
        }

        if(entity.timer.get(timerBattery, 30)){
            if(entity.items.has(Items.graphite, 40)){
                nearby.clear();

                Geometry.circle(tile.x, tile.y, 8, (x, y) -> nearby.add(world.ltile(x, y)));
                nearby.removeAll(t -> t.block != Blocks.battery);
                nearby.removeAll(t -> t.entity.power.status != 0f);
                Tile battery = nearby.random();

                if(battery != null){
                    battery.entity.power.status = 1f;

                    BulletType graphite = Bullets.artilleryDense;

                    float dst = tile.dst(battery);
                    float maxTraveled = graphite.lifetime * graphite.speed;

                    Call.createBullet(graphite, tile.getTeam(), tile.drawx(), tile.drawy(), tile.angleTo(battery), 1, (dst / maxTraveled));
                    entity.items.remove(Items.graphite, 40);

                    Timer.schedule(() -> {
                        Call.onEffect(Fx.dooropen, battery.drawx(), battery.drawy(), 0, Color.white);
                        netServer.titanic.add(battery);
                    }, 0.005f * dst);
                }
            }
        }

        if(entity.spawnPlayer != null){
            if(!entity.spawnPlayer.isDead() || !entity.spawnPlayer.isAdded()){
                entity.spawnPlayer = null;
                return;
            }

            entity.spawnPlayer.set(tile.drawx(), tile.drawy());
            entity.heat = Mathf.lerpDelta(entity.heat, 1f, 0.1f);
            entity.time += entity.delta();
            entity.progress += 1f / state.rules.respawnTime * entity.delta();

            if(entity.progress >= 1f){
                Call.onUnitRespawn(tile, entity.spawnPlayer);
            }
        }else{
            entity.heat = Mathf.lerpDelta(entity.heat, 0f, 0.1f);
        }
    }

    @Override
    public boolean shouldActiveSound(Tile tile){
        CoreEntity entity = tile.ent();

        return entity.spawnPlayer != null;
    }

    public class CoreEntity extends TileEntity implements SpawnerTrait, FactoryTrait{
        protected Player spawnPlayer;
        protected float progress;
        protected float time;
        protected float heat;
        protected int storageCapacity;

        @Override
        public boolean hasUnit(Unit unit){
            return unit == spawnPlayer;
        }

        @Override
        public void updateSpawning(Player player){
            if(!netServer.isWaitingForPlayers() && spawnPlayer == null){
                spawnPlayer = player;
                progress = 0f;
                player.mech = mech;
                player.beginRespawning(this);
            }
        }
    }
}
