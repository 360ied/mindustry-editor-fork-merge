package mindustry.world.blocks.power;

import arc.*;
import arc.func.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.math.*;
import arc.math.geom.*;
import arc.struct.*;
import arc.util.ArcAnnotate.*;
import arc.util.*;
import mindustry.entities.units.*;
import mindustry.gen.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.meta.*;

import static mindustry.Vars.*;

public class PowerNode extends PowerBlock{
    protected static boolean returnValue = false;
    protected static BuildRequest otherReq;

    protected final ObjectSet<PowerGraph> graphs = new ObjectSet<>();
    protected final Vec2 t1 = new Vec2(), t2 = new Vec2();

    public TextureRegion laser, laserEnd;
    public float laserRange = 6;
    public int maxNodes = 3;

    public PowerNode(String name){
        super(name);
        expanded = true;
        layer = Layer.power;
        configurable = true;
        consumesPower = false;
        outputsPower = false;
        entityType = PowerNodeEntity::new;

        config(Integer.class, (tile, value) -> {
            Tilec entity = tile.entity;
            Tile other = world.tile(value);
            boolean contains = entity.power().links.contains(value), valid = other != null && other.entity != null && other.entity.power() != null;

            if(contains){
                //unlink
                entity.power().links.removeValue(value);
                if(valid) other.entity.power().links.removeValue(tile.pos());

                PowerGraph newgraph = new PowerGraph();

                //reflow from this point, covering all tiles on this side
                newgraph.reflow(tile);

                if(valid && other.entity.power().graph != newgraph){
                    //create new graph for other end
                    PowerGraph og = new PowerGraph();
                    //reflow from other end
                    og.reflow(other);
                }
            }else if(linkValid(tile, other) && valid && entity.power().links.size < maxNodes){

                if(!entity.power().links.contains(other.pos())){
                    entity.power().links.add(other.pos());
                }

                if(other.getTeamID() == tile.getTeamID()){

                    if(!other.entity.power().links.contains(tile.pos())){
                        other.entity.power().links.add(tile.pos());
                    }
                }

                entity.power().graph.add(other.entity.power().graph);
            }
        });

        config(Point2[].class, (tile, value) -> {
            tile.entity.power().links.clear();
            for(Point2 p : value){
                if(tile.entity.power().links.size < maxNodes){
                    tile.entity.power().links.add(Point2.pack(p.x + tile.x, p.y + tile.y));
                }
            }
        });
    }

    @Override
    public void load(){
        super.load();

        laser = Core.atlas.find("laser");
        laserEnd = Core.atlas.find("laser-end");
    }

    @Override
    public void setBars(){
        super.setBars();
        bars.add("power", entity -> new Bar(() ->
        Core.bundle.format("bar.powerbalance",
        ((entity.power().graph.getPowerBalance() >= 0 ? "+" : "") + Strings.fixed(entity.power().graph.getPowerBalance() * 60, 1))),
        () -> Pal.powerBar,
        () -> Mathf.clamp(entity.power().graph.getLastPowerProduced() / entity.power().graph.getLastPowerNeeded())));

        bars.add("batteries", entity -> new Bar(() ->
        Core.bundle.format("bar.powerstored",
        (ui.formatAmount((int)entity.power().graph.getBatteryStored())), ui.formatAmount((int)entity.power().graph.getTotalBatteryCapacity())),
        () -> Pal.powerBar,
        () -> Mathf.clamp(entity.power().graph.getBatteryStored() / entity.power().graph.getTotalBatteryCapacity())));
    }

    @Override
    public void placed(Tile tile){
        if(net.client()) return;

        Boolf<Tile> valid = other -> other != null && other != tile && ((!other.block().outputsPower && other.block().consumesPower) || (other.block().outputsPower && !other.block().consumesPower) || other.block() instanceof PowerNode) && linkValid(tile, other)
        && !other.entity.proximity().contains(tile) && other.entity.power().graph != tile.entity.power().graph;

        tempTiles.clear();
        Geometry.circle(tile.x, tile.y, (int)(laserRange + 2), (x, y) -> {
            Tile other = world.ltile(x, y);
            if(valid.get(other)){
                if(!insulated(tile, other)){
                    tempTiles.add(other);
                }
            }
        });

        tempTiles.sort((a, b) -> {
            int type = -Boolean.compare(a.block() instanceof PowerNode, b.block() instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(a.dst2(tile), b.dst2(tile));
        });
        tempTiles.each(valid, other -> {
            if(!tile.entity.power().links.contains(other.pos())){
                tile.configureAny(other.pos());
            }
        });

        super.placed(tile);
    }

    private void getPotentialLinks(Tile tile, Cons<Tile> others){
        Boolf<Tile> valid = other -> other != null && other != tile && other.entity != null && other.entity.power() != null &&
        ((!other.block().outputsPower && other.block().consumesPower) || (other.block().outputsPower && !other.block().consumesPower) || other.block() instanceof PowerNode) &&
        overlaps(tile.x * tilesize + offset(), tile.y * tilesize + offset(), other, laserRange * tilesize) && other.team() == player.team()
        && !other.entity.proximity().contains(tile) && !graphs.contains(other.entity.power().graph);

        tempTiles.clear();
        graphs.clear();
        Geometry.circle(tile.x, tile.y, (int)(laserRange + 2), (x, y) -> {
            Tile other = world.ltile(x, y);
            if(valid.get(other) && !tempTiles.contains(other)){
                tempTiles.add(other);
            }
        });

        tempTiles.sort((a, b) -> {
            int type = -Boolean.compare(a.block() instanceof PowerNode, b.block() instanceof PowerNode);
            if(type != 0) return type;
            return Float.compare(a.dst2(tile), b.dst2(tile));
        });
        tempTiles.each(valid, t -> {
            graphs.add(t.entity.power().graph);
            others.get(t);
        });
    }

    @Override
    public void setStats(){
        super.setStats();

        stats.add(BlockStat.powerRange, laserRange, StatUnit.blocks);
        stats.add(BlockStat.powerConnections, maxNodes, StatUnit.none);
    }

    @Override
    public void updateTile(){
        tile.entity.power().graph.update();
    }

    @Override
    public boolean onConfigureTileTapped(Tile tile, Tile other){
        Tilec entity = tile.ent();
        other = other.link();

        if(linkValid(tile, other)){
            tile.configure(other.pos());
            return false;
        }

        if(tile == other){
            if(other.entity.power().links.size == 0){
                int[] total = {0};
                getPotentialLinks(tile, link -> {
                    if(!insulated(tile, link) && total[0]++ < maxNodes){
                        tile.configure(link.pos());
                    }
                });
            }else{
                while(entity.power().links.size > 0){
                    tile.configure(entity.power().links.get(0));
                }
            }
            return false;
        }

        return true;
    }

    @Override
    public void drawSelect(Tile tile){
        super.drawSelect(tile);

        Lines.stroke(1f);

        Draw.color(Pal.accent);
        Drawf.circles(tile.drawx(), tile.drawy(), laserRange * tilesize);
        Draw.reset();
    }

    @Override
    public void drawConfigure(Tile tile){

        Draw.color(Pal.accent);

        Lines.stroke(1.5f);
        Lines.circle(tile.drawx(), tile.drawy(),
        tile.block().size * tilesize / 2f + 1f + Mathf.absin(Time.time(), 4f, 1f));

        Drawf.circles(tile.drawx(), tile.drawy(), laserRange * tilesize);

        Lines.stroke(1.5f);

        for(int x = (int)(tile.x - laserRange - 2); x <= tile.x + laserRange + 2; x++){
            for(int y = (int)(tile.y - laserRange - 2); y <= tile.y + laserRange + 2; y++){
                Tile link = world.ltile(x, y);

                if(link != tile && linkValid(tile, link, false)){
                    boolean linked = linked(tile, link);

                    if(linked){
                        Drawf.square(link.drawx(), link.drawy(), link.block().size * tilesize / 2f + 1f, Pal.place);
                    }
                }
            }
        }

        Draw.reset();
    }

    @Override
    public void drawPlace(int x, int y, int rotation, boolean valid){
        Tile tile = world.tile(x, y);

        if(tile == null) return;

        Lines.stroke(1f);
        Draw.color(Pal.placing);
        Drawf.circles(x * tilesize + offset(), y * tilesize + offset(), laserRange * tilesize);

        getPotentialLinks(tile, other -> {
            Drawf.square(other.drawx(), other.drawy(), other.block().size * tilesize / 2f + 2f, Pal.place);

            insulators(tile.x, tile.y, other.x, other.y, cause -> {
                Drawf.square(cause.drawx(), cause.drawy(), cause.block().size * tilesize / 2f + 2f, Pal.plastanium);
            });
        });

        Draw.reset();
    }

    @Override
    public void drawLayer(Tile tile){
        if(Core.settings.getInt("lasersopacity") == 0) return;

        Tilec entity = tile.ent();

        for(int i = 0; i < entity.power().links.size; i++){
            Tile link = world.tile(entity.power().links.get(i));

            if(!linkValid(tile, link)) continue;

            if(link.block() instanceof PowerNode && !(link.pos() < tile.pos())) continue;

            drawLaser(tile, link);
        }

        Draw.reset();
    }

    @Override
    public void drawRequestConfigTop(BuildRequest req, Eachable<BuildRequest> list){
        if(req.config instanceof Point2[]){
            for(Point2 point : (Point2[])req.config){
                otherReq = null;
                list.each(other -> {
                    if((other.x == req.x + point.x && other.y == req.y + point.y) && other != req){
                        otherReq = other;
                    }
                });

                if(otherReq == null || otherReq.block == null) return;

                drawLaser(req.drawx(), req.drawy(), otherReq.drawx(), otherReq.drawy(), 1f, size, otherReq.block.size);
            }
        }
    }

    protected boolean linked(Tile tile, Tile other){
        return tile.entity.power().links.contains(other.pos());
    }

    public boolean linkValid(Tilec tile, Tilec link){
        return linkValid(tile, link, true);
    }

    public boolean linkValid(Tilec tile, Tilec link, boolean checkMaxNodes){
        if(tile == link || link == null || !link.block().hasPower || tile.team() != link.team()) return false;

        if(overlaps(tile, link, laserRange * tilesize) || (link.block() instanceof PowerNode && overlaps(link, tile, link.<PowerNode>cblock().laserRange * tilesize))){
            if(checkMaxNodes && link.block() instanceof PowerNode){
                return link.entity.power().links.size < link.<PowerNode>cblock().maxNodes || link.entity.power().links.contains(tile.pos());
            }
            return true;
        }
        return false;
    }

    protected boolean overlaps(float srcx, float srcy, Tile other, float range){
        return Intersector.overlaps(Tmp.cr1.set(srcx, srcy, range), other.getHitbox(Tmp.r1));
    }

    protected boolean overlaps(Tile src, Tile other, float range){
        return overlaps(src.drawx(), src.drawy(), other, range);
    }

    public boolean overlaps(@Nullable Tile src, @Nullable Tile other){
        if(src == null || other == null) return true;
        return Intersector.overlaps(Tmp.cr1.set(src.worldx() + offset(), src.worldy() + offset(), laserRange * tilesize), Tmp.r1.setSize(size * tilesize).setCenter(other.worldx() + offset(), other.worldy() + offset()));
    }

    protected void drawLaser(Tile tile, Tile target){
        drawLaser(tile.drawx(), tile.drawy(), target.drawx(), target.drawy(), tile.entity.power().graph.getSatisfaction(), size, target.block().size);
    }

    protected void drawLaser(float x1, float y1, float x2, float y2, float satisfaction, int size1, int size2){
        int opacityPercentage = Core.settings.getInt("lasersopacity");
        if(opacityPercentage == 0) return;

        float opacity = opacityPercentage / 100f;

        float angle1 = Angles.angle(x1, y1, x2, y2);
        t1.trns(angle1, size1 * tilesize / 2f - 1.5f);
        t2.trns(angle1 + 180f, size2 * tilesize / 2f - 1.5f);

        x1 += t1.x;
        y1 += t1.y;
        x2 += t2.x;
        y2 += t2.y;

        float fract = 1f - satisfaction;

        Draw.color(Color.white, Pal.powerLight, fract * 0.86f + Mathf.absin(3f, 0.1f));
        Draw.alpha(opacity);
        Drawf.laser(laser, laserEnd, x1, y1, x2, y2, 0.25f);
        Draw.color();
    }

    public static boolean insulated(Tilec tile, Tilec other){
        return insulated(tile.tileX(), tile.tileY(), other.tileX(), other.tileY());
    }

    public static boolean insulated(int x, int y, int x2, int y2){
        returnValue = false;
        insulators(x, y, x2, y2, cause -> returnValue = true);
        return returnValue;
    }

    public static void insulators(int x, int y, int x2, int y2, Cons<Tile> iterator){
        world.raycastEach(x, y, x2, y2, (wx, wy) -> {

            Tile tile = world.ltile(wx, wy);
            if(tile != null && tile.block() != null && tile.block().insulated){
                iterator.get(tile);
            }

            return false;
        });
    }

    public class PowerNodeEntity extends TileEntity{

        @Override
        public Point2[] config(){
            Point2[] out = new Point2[power.links.size];
            for(int i = 0; i < out.length; i++){
                out[i] = Point2.unpack(power.links.get(i)).sub(tile.x, tile.y);
            }
            return out;
        }
    }
}
