package mindustry.world.blocks.production;

import arc.func.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.util.*;
import arc.util.io.*;
import mindustry.content.*;
import mindustry.entities.AllDefs.*;
import mindustry.entities.*;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.consumers.*;
import mindustry.world.meta.*;

public class GenericCrafter extends Block{
    public ItemStack outputItem;
    public LiquidStack outputLiquid;

    public float craftTime = 80;
    public Effect craftEffect = Fx.none;
    public Effect updateEffect = Fx.none;
    public float updateEffectChance = 0.04f;

    public Cons<GenericCrafterEntity> drawer = null;
    public Prov<TextureRegion[]> drawIcons = null;

    public GenericCrafter(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        health = 60;
        idleSound = Sounds.machine;
        sync = true;
        idleSoundVolume = 0.03f;
        entityType = GenericCrafterEntity::new;
    }

    @Override
    public void setStats(){
        if(consumes.has(ConsumeType.liquid)){
            ConsumeLiquidBase cons = consumes.get(ConsumeType.liquid);
            cons.timePeriod = craftTime;
        }

        super.setStats();
        stats.add(BlockStat.productionTime, craftTime / 60f, StatUnit.seconds);

        if(outputItem != null){
            stats.add(BlockStat.output, outputItem);
        }

        if(outputLiquid != null){
            stats.add(BlockStat.output, outputLiquid.liquid, outputLiquid.amount, false);
        }
    }

    @Override
    public boolean shouldIdleSound(Tilec tile){
        return tile.cons().valid();
    }

    @Override
    public void init(){
        outputsLiquid = outputLiquid != null;
        super.init();
    }

    @Override
    public void draw(){
        if(drawer == null){
            super.draw(tile);
        }else{
            drawer.get(tile);
        }
    }

    @Override
    public TextureRegion[] generateIcons(){
        return drawIcons == null ? super.generateIcons() : drawIcons.get();
    }

    @Override
    public void updateTile(){
        GenericCrafterEntity entity = tile.ent();

        if(entity.consValid()){

            entity.progress += getProgressIncrease(entity, craftTime);
            entity.totalProgress += entity.delta();
            entity.warmup = Mathf.lerpDelta(entity.warmup, 1f, 0.02f);

            if(Mathf.chance(Time.delta() * updateEffectChance)){
                updateEffect.at(entity.getX() + Mathf.range(size * 4f), entity.getY() + Mathf.range(size * 4));
            }
        }else{
            entity.warmup = Mathf.lerp(entity.warmup, 0f, 0.02f);
        }

        if(entity.progress >= 1f){
            entity.consume();

            if(outputItem != null){
                useContent(tile, outputItem.item);
                for(int i = 0; i < outputItem.amount; i++){
                    offloadNear(tile, outputItem.item);
                }
            }

            if(outputLiquid != null){
                useContent(tile, outputLiquid.liquid);
                handleLiquid(tile, tile, outputLiquid.liquid, outputLiquid.amount);
            }

            craftEffect.at(tile.drawx(), tile.drawy());
            entity.progress = 0f;
        }

        if(outputItem != null && tile.entity.timer(timerDump, dumpTime)){
            tryDump(tile, outputItem.item);
        }

        if(outputLiquid != null){
            tryDumpLiquid(tile, outputLiquid.liquid);
        }
    }

    @Override
    public boolean outputsItems(){
        return outputItem != null;
    }

    @Override
    public boolean shouldConsume(Tile tile){
        if(outputItem != null && tile.entity.items().get(outputItem.item) >= itemCapacity){
            return false;
        }
        return outputLiquid == null || !(tile.entity.liquids().get(outputLiquid.liquid) >= liquidCapacity - 0.001f);
    }

    @Override
    public int getMaximumAccepted(Tile tile, Item item){
        return itemCapacity;
    }

    public static class GenericCrafterEntity extends TileEntity{
        public float progress;
        public float totalProgress;
        public float warmup;

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(progress);
            write.f(warmup);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            progress = read.f();
            warmup = read.f();
        }
    }
}
