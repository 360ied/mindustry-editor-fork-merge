package mindustry.world.blocks.storage;

import arc.*;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.gen.*;
import mindustry.game.EventType.*;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.ItemType;
import mindustry.world.Tile;
import mindustry.world.meta.BlockStat;
import mindustry.world.meta.StatUnit;

import static mindustry.Vars.*;

public class LaunchPad extends StorageBlock{
    public final int timerLaunch = timers++;
    /** Time inbetween launches. */
    public float launchTime;

    public LaunchPad(String name){
        super(name);
        update = true;
        hasItems = true;
        solid = true;
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.launchTime, launchTime / 60f, StatUnit.seconds);
    }

    @Override
    public boolean acceptItem(Tile tile, Tile source, Item item){
        return item.type == ItemType.material && tile.entity.items().total() < itemCapacity;
    }

    @Override
    public void draw(){
        super.draw(tile);

        //TODO broken
        float progress = Mathf.clamp(Mathf.clamp((tile.entity.items().total() / (float)itemCapacity)) * ((tile.entity.timer().getTime(timerLaunch) / (launchTime / tile.entity.timeScale()))));
        float scale = size / 3f;

        Lines.stroke(2f);
        Draw.color(Pal.accentBack);
        Lines.poly(tile.drawx(), tile.drawy(), 4, scale * 10f * (1f - progress), 45 + 360f * progress);

        Draw.color(Pal.accent);

        if(tile.entity.cons().valid()){
            for(int i = 0; i < 3; i++){
                float f = (Time.time() / 200f + i * 0.5f) % 1f;

                Lines.stroke(((2f * (2f - Math.abs(0.5f - f) * 2f)) - 2f + 0.2f));
                Lines.poly(tile.drawx(), tile.drawy(), 4, (1f - f) * 10f * scale);
            }
        }

        Draw.reset();
    }

    @Override
    public void updateTile(){
        Tilec entity = tile.entity;

        if(state.isCampaign() && entity.consValid() && entity.items().total() >= itemCapacity && entity.timer(timerLaunch, launchTime / entity.timeScale())){
            for(Item item : Vars.content.items()){
                Events.fire(Trigger.itemLaunch);
                Fx.padlaunch.at(tile);
                int used = Math.min(entity.items().get(item), itemCapacity);
                data.addItem(item, used);
                entity.items().remove(item, used);
                Events.fire(new LaunchItemEvent(item, used));
            }
        }
    }
}
