package io.anuke.mindustry.world.blocks.units;

import io.anuke.annotations.Annotations.Loc;
import io.anuke.annotations.Annotations.Remote;
import io.anuke.arc.*;
import io.anuke.arc.collection.EnumSet;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.math.Mathf;
import io.anuke.mindustry.Vars;
import io.anuke.mindustry.content.Fx;
import io.anuke.mindustry.content.UnitTypes;
import io.anuke.mindustry.entities.Effects;
import io.anuke.mindustry.entities.type.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.gen.Call;
import io.anuke.mindustry.graphics.Pal;
import io.anuke.mindustry.graphics.Shaders;
import io.anuke.mindustry.type.*;
import io.anuke.mindustry.ui.Bar;
import io.anuke.mindustry.ui.Cicon;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Pos;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.consumers.ConsumeItems;
import io.anuke.mindustry.world.consumers.ConsumeType;
import io.anuke.mindustry.world.meta.*;

import java.io.*;
import static io.anuke.mindustry.Vars.*;

public class UnitFactory extends Block{
    protected UnitType unitType;
    protected float produceTime = 1000f;
    protected float launchVelocity = 0f;
    protected TextureRegion topRegion;
    protected int maxSpawn = 4;
    protected int[] capacities;

    public UnitFactory(String name){
        super(name);
        update = true;
        hasPower = true;
        hasItems = true;
        solid = false;
        flags = EnumSet.of(BlockFlag.producer);
    }

    @Remote(called = Loc.server)
    public static void onUnitFactorySpawn(Tile tile, int spawns){
        if(!(tile.entity instanceof UnitFactoryEntity) || !(tile.block() instanceof UnitFactory)) return;

        UnitFactoryEntity entity = tile.entity();
        UnitFactory factory = (UnitFactory)tile.block();

        entity.buildTime = 0f;
        entity.spawned = spawns;

        Effects.shake(2f, 3f, entity);
        Effects.effect(Fx.producesmoke, tile.drawx(), tile.drawy());

        if(!net.client()){
            BaseUnit unit = factory.unitType.create(tile.getTeam());
            unit.setSpawner(tile);
            unit.set(tile.drawx() + Mathf.range(4), tile.drawy() + Mathf.range(4));
            unit.add();
            unit.velocity().y = factory.launchVelocity;
            Events.fire(new UnitCreateEvent(unit));
        }
    }

    @Override
    public void init(){
        super.init();

        capacities = new int[Vars.content.items().size];
        if(consumes.has(ConsumeType.item)){
            ConsumeItems cons = consumes.get(ConsumeType.item);
            for(ItemStack stack : cons.items){
                capacities[stack.item.id] = stack.amount * 2;
            }
        }
    }

    @Override
    public void load(){
        super.load();

        topRegion = Core.atlas.find(name + "-top");

        if(unitType == UnitTypes.draug){
            configurable = true;
        }
    }

    @Override
    public boolean onConfigureTileTapped(Tile tile, Tile other) {
        UnitFactoryEntity entity = (UnitFactoryEntity) tile.entity;

        if(linkValid(tile, other)){
            if(entity.link == other.pos()){
                tile.configure(Pos.invalid);
            }else{
                tile.configure(other.pos());
            }
            return false;
        }

        return true;
    }

    @Override
    public void configured(Tile tile, Player player, int value){
        tile.<UnitFactoryEntity>entity().link = value;
    }

    @Override
    public void drawConfigure(Tile tile){
        UnitFactoryEntity entity = tile.entity();

        // yellow around factory itself
        Draw.color(Pal.accent);
        Lines.stroke(1f);
        Lines.square(tile.drawx(), tile.drawy(), tile.block().size * tilesize / 2f + 1f);

        // red for each possible dropoff point
        Draw.color(Pal.breakInvalid);
        for(int x = 0; x < world.width(); x++){
            for(int y = 0; y < world.height(); y++){
                // yes yes, i know this should probably use a range or indexer, but this is a draft <3
                Tile chesspiece = world.ltile(x, y);

                if(linkValid(tile, chesspiece)){
                    Lines.square(chesspiece.drawx(), chesspiece.drawy(), chesspiece.block().size * tilesize / 2f + 1f + 0f);
                }
            }
        }

        // overline one of the reds with blue when linked
        if(entity.link != Pos.invalid){
            Draw.color(Pal.place);
            Tile other = world.tile(entity.link);
            Lines.square(other.drawx(), other.drawy(), other.block().size * tilesize / 2f + 1f + 0f);
        }

        Draw.reset();
    }

    // order of these checks subject to change, this just visually looks somewhat orderly
    public boolean linkValid(Tile tile, Tile other){
        if(other == null) return false;
        if(other == tile) return false;
        if(!other.block().hasItems) return false;
        if(other.getTeam() != tile.getTeam()) return false;

        return true;
    }


    @Override
    public void setBars(){
        super.setBars();
        bars.add("progress", entity -> new Bar("bar.progress", Pal.ammo, () -> ((UnitFactoryEntity)entity).buildTime / produceTime));
        bars.add("spawned", entity -> new Bar(() -> Core.bundle.format("bar.spawned", ((UnitFactoryEntity)entity).spawned, maxSpawn), () -> Pal.command, () -> (float)((UnitFactoryEntity)entity).spawned / maxSpawn));
    }

    @Override
    public boolean outputsItems(){
        return false;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.remove(BlockStat.itemCapacity);
        stats.add(BlockStat.productionTime, produceTime / 60f, StatUnit.seconds);
        stats.add(BlockStat.maxUnits, maxSpawn, StatUnit.none);
    }

    @Override
    public void unitRemoved(Tile tile, Unit unit){
        UnitFactoryEntity entity = tile.entity();
        entity.spawned--;
        entity.spawned = Math.max(entity.spawned, 0);
    }

    @Override
    public TextureRegion[] generateIcons(){
        return new TextureRegion[]{Core.atlas.find(name), Core.atlas.find(name + "-top")};
    }

    @Override
    public void draw(Tile tile){
        UnitFactoryEntity entity = tile.entity();
        TextureRegion region = unitType.icon(Cicon.full);

        Draw.rect(name, tile.drawx(), tile.drawy());

        Shaders.build.region = region;
        Shaders.build.progress = entity.buildTime / produceTime;
        Shaders.build.color.set(Pal.accent);
        Shaders.build.color.a = entity.speedScl;
        Shaders.build.time = -entity.time / 20f;

        Draw.shader(Shaders.build);
        Draw.rect(region, tile.drawx(), tile.drawy());
        Draw.shader();

        Draw.color(Pal.accent);
        Draw.alpha(entity.speedScl);

        Lines.lineAngleCenter(
        tile.drawx() + Mathf.sin(entity.time, 20f, Vars.tilesize / 2f * size - 2f),
        tile.drawy(),
        90,
        size * Vars.tilesize - 4f);

        Draw.reset();

        Draw.rect(topRegion, tile.drawx(), tile.drawy());
    }

    @Override
    public void update(Tile tile){
        UnitFactoryEntity entity = tile.entity();

        if(entity.spawned >= maxSpawn){
            return;
        }

        if(entity.cons.valid() || tile.isEnemyCheat()){
            entity.time += entity.delta() * entity.speedScl * Vars.state.rules.unitBuildSpeedMultiplier * entity.efficiency();
            entity.buildTime += entity.delta() * entity.efficiency() * Vars.state.rules.unitBuildSpeedMultiplier;
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 1f, 0.05f);
        }else{
            entity.speedScl = Mathf.lerpDelta(entity.speedScl, 0f, 0.05f);
        }

        if(entity.buildTime >= produceTime){
            entity.buildTime = 0f;

            Call.onUnitFactorySpawn(tile, entity.spawned + 1);
            useContent(tile, unitType);

            entity.cons.trigger();
        }
    }
    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        return capacities[item.id];
    }

    @Override
    public TileEntity newEntity(){
        return new UnitFactoryEntity();
    }

    @Override
    public boolean shouldConsume(Tile tile){
        UnitFactoryEntity entity = tile.entity();
        return entity.spawned < maxSpawn;
    }

    public static class UnitFactoryEntity extends TileEntity{
        float buildTime;
        float time;
        float speedScl;
        int spawned;
        public int link = Pos.invalid;

        @Override
        public void write(DataOutput stream) throws IOException{
            super.write(stream);
            stream.writeFloat(buildTime);
            stream.writeInt(spawned);
            stream.writeInt(link);
        }

        @Override
        public void read(DataInput stream, byte revision) throws IOException{
            super.read(stream, revision);
            buildTime = stream.readFloat();
            spawned = stream.readInt();
            link = stream.readInt();
        }
    }
}
