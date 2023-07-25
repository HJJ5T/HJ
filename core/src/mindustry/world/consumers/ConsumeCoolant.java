package mindustry.world.consumers;

/** A ConsumeLiquidFilter that consumes specific coolant, selected based on stats. */
public class ConsumeCoolant extends ConsumeLiquidFilter{
    public float maxTemp = 0.5f, maxFlammability = 0.1f;
    public boolean allowLiquid = true, allowGas = false;

    public ConsumeCoolant(float amount){
        this.filter = liquid -> liquid.coolant && (allowLiquid && !liquid.gas || allowGas && liquid.gas) && liquid.temperature <= maxTemp && liquid.flammability < maxFlammability;
        this.amount = amount;
    }

    public ConsumeCoolant(){
        this(1f);
    }
}
