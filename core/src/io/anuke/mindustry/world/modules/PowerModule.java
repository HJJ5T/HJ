package io.anuke.mindustry.world.modules;

import com.badlogic.gdx.utils.IntArray;
import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.world.blocks.power.PowerGraph;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class PowerModule extends BlockModule{
    // Initialization is required to prevent blocks from having NaN power and sucks all the power away.
    public float amount = 0f;
    public byte priority = 5;
    public PowerGraph graph = new PowerGraph();
    public IntArray links = new IntArray();

    public void update(TileEntity entity){
        graph.update();
    }

    @Override
    public void write(DataOutput stream) throws IOException{
        stream.writeFloat(amount);
        stream.writeByte(priority);

        stream.writeShort(links.size);
        for(int i = 0; i < links.size; i++){
            stream.writeInt(links.get(i));
        }
    }

    @Override
    public void read(DataInput stream) throws IOException{
        amount = stream.readFloat();
        // Workaround: If power went negative for some reason, at least fix it when reloading the map
        if(amount < 0f){
            amount = 0f;
        }

        priority = stream.readByte();
        // Priority should be between 1 and 9.
        if(priority < 1) priority = 1;
        if(priority > 9) priority = 9;

        short amount = stream.readShort();
        for(int i = 0; i < amount; i++){
            links.add(stream.readInt());
        }
    }
}
