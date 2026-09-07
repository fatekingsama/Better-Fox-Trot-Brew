package com.chinaex123.fox_trot_brew.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IPressingTub;
import com.github.ysbbbbbb.kaleidoscopetavern.crafting.recipe.PressingTubRecipe;
import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidActionResult;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.*;

/** One bounded visit per tub; the tavern's normal fall handler performs pressing. */
public class MaidPressingTubBehavior extends MaidCheckRateTask {
    private static final int BUCKET = 1000;
    private final float movementSpeed;
    private final Map<BlockPos, Long> cooldowns = new HashMap<>();
    private final Map<BlockPos, Long> lastVisits = new HashMap<>();
    private List<PressingTubRecipe> recipes = List.of();
    private BlockPos target;
    private BlockPos approach;
    private long travelStarted, visitStarted, lastProgress, nextJump;
    private int previousFruit, previousFluid;
    private boolean finished;

    public MaidPressingTubBehavior(float movementSpeed) {
        super(ImmutableMap.of(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                InitEntities.TARGET_POS.get(), MemoryStatus.REGISTERED));
        this.movementSpeed = movementSpeed;
        setMaxCheckRate(20);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, EntityMaid maid) {
        if (!super.checkExtraStartConditions(world, maid)) return false;
        long now = world.getGameTime();
        cooldowns.entrySet().removeIf(e -> e.getValue() <= now);
        lastVisits.entrySet().removeIf(e -> now - e.getValue() > 12000);
        recipes = world.getRecipeManager().getRecipes().stream()
                .filter(PressingTubRecipe.class::isInstance).map(PressingTubRecipe.class::cast).toList();
        if (target != null && (now - travelStarted > 200 || !allowed(world, maid, target)
                || action(world, maid, target) == TubWorkPolicy.Action.NONE)) {
            cooldowns.put(target, now + 100);
            target = null;
            maid.getNavigation().stop();
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        if (target == null) {
            target = findTub(world, maid);
            travelStarted = now;
        }
        if (target == null) return false;
        if (horizontalDistance(maid, target) < 1.44 && Math.abs(maid.getY() - target.getY()) < 1.5) {
            maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            return true;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(maid, approach == null ? target : approach, movementSpeed, 0);
        return false;
    }

    @Override
    protected void start(ServerLevel world, EntityMaid maid, long time) {
        maid.getNavigation().stop();
        finished = false;
        visitStarted = lastProgress = nextJump = time;
        IPressingTub tub = (IPressingTub) world.getBlockEntity(target);
        remember(tub);
    }

    @Override
    protected boolean timedOut(long time) {
        // Use the visit/progress limits below instead of Behavior's default 60 ticks.
        return false;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, EntityMaid maid, long time) {
        return !finished && target != null && allowed(world, maid, target)
                && horizontalDistance(maid, target) < 2.25
                && Math.abs(maid.getY() - target.getY()) < 2.5
                && !TubWorkPolicy.expired(time - visitStarted, time - lastProgress);
    }

    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long time) {
        if (!(world.getBlockEntity(target) instanceof IPressingTub tub)) { finished = true; return; }
        if (previousFruit != tub.getItems().getStackInSlot(0).getCount()
                || previousFluid != tub.getFluidAmount()) {
            lastProgress = time;
            remember(tub);
        }
        switch (action(world, maid, target)) {
            case COLLECT -> {
                collect(maid, tub);
                finished = true;
            }
            case FEED -> {
                feed(maid, tub);
                // Feeding failure must not hold this tub indefinitely.
                finished = !canPress(tub, tub.getItems().getStackInSlot(0));
            }
            case PRESS -> {
                double dx = target.getX() + 0.5 - maid.getX();
                double dz = target.getZ() + 0.5 - maid.getZ();
                maid.setDeltaMovement(dx * 0.1, maid.getDeltaMovement().y, dz * 0.1);
                if (maid.onGround() && time >= nextJump) {
                    maid.setDeltaMovement(maid.getDeltaMovement().add(0, 0.42, 0));
                    nextJump = time + 12;
                }
            }
            case NONE -> finished = true;
        }
    }

    @Override
    protected void stop(ServerLevel world, EntityMaid maid, long time) {
        super.stop(world, maid, time);
        if (target != null) {
            cooldowns.put(target, time + (finished ? 40 : 100));
            lastVisits.put(target, time);
        }
        target = null;
        maid.getNavigation().stop();
        maid.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }

    private void remember(IPressingTub tub) {
        previousFruit = tub.getItems().getStackInSlot(0).getCount();
        previousFluid = tub.getFluidAmount();
    }

    private static double horizontalDistance(EntityMaid maid, BlockPos pos) {
        return maid.distanceToSqr(pos.getX() + 0.5, maid.getY(), pos.getZ() + 0.5);
    }

    private boolean allowed(ServerLevel world, EntityMaid maid, BlockPos pos) {
        return maid.isWithinRestriction(pos) && world.hasChunkAt(pos)
                && world.getBlockEntity(pos) instanceof IPressingTub;
    }

    private BlockPos findTub(ServerLevel world, EntityMaid maid) {
        BlockPos center = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition();
        int range = maid.hasRestriction() ? (int) maid.getRestrictRadius() : 16;
        List<BlockPos> collect = new ArrayList<>(), work = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -4, -range), center.offset(range, 4, range))) {
            if (cooldowns.containsKey(pos) || !allowed(world, maid, pos)) continue;
            TubWorkPolicy.Action action = action(world, maid, pos);
            if (action == TubWorkPolicy.Action.COLLECT) collect.add(pos.immutable());
            else if (action != TubWorkPolicy.Action.NONE) work.add(pos.immutable());
        }
        Comparator<BlockPos> nearest = Comparator
                .comparingLong((BlockPos p) -> lastVisits.getOrDefault(p, Long.MIN_VALUE))
                .thenComparingDouble(p -> p.distToCenterSqr(maid.position()));
        collect.sort(nearest);
        work.sort(nearest);
        collect.addAll(work);
        MaidPathFindingBFS paths = new MaidPathFindingBFS(maid.getNavigation().getNodeEvaluator(), world, maid);
        try {
            for (BlockPos pos : collect) {
                TubWorkPolicy.Action next = action(world, maid, pos);
                // A basin is an interaction target, not necessarily a walkable feet node.
                List<BlockPos> feet = new ArrayList<>();
                if (next != TubWorkPolicy.Action.PRESS) {
                    feet.add(pos.north()); feet.add(pos.south());
                    feet.add(pos.east()); feet.add(pos.west());
                }
                feet.add(pos.above());
                feet.add(pos);
                feet.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(maid.position())));
                for (BlockPos foot : feet) {
                    if (maid.isWithinRestriction(foot) && paths.canPathReach(foot)) {
                        approach = foot;
                        return pos;
                    }
                }
                cooldowns.put(pos, world.getGameTime() + 100);
            }
        } finally {
            paths.finish();
        }
        return null;
    }

    private TubWorkPolicy.Action action(ServerLevel world, EntityMaid maid, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof IPressingTub tub)) return TubWorkPolicy.Action.NONE;
        IItemHandler inventory = workInventory(maid);
        boolean full = tub.getFluidAmount() >= BUCKET;
        return TubWorkPolicy.choose(full, full && bucketSlot(inventory, tub) >= 0,
                !full && canPress(tub, tub.getItems().getStackInSlot(0)),
                !full && feedSlot(inventory, tub) >= 0);
    }

    private boolean canPress(IPressingTub tub, ItemStack fruit) {
        if (fruit.isEmpty()) return false;
        for (PressingTubRecipe recipe : recipes) {
            if (recipe.getIngredient().test(fruit)) {
                int amount = recipe.getFluidAmount();
                return amount > 0 && tub.getFluid().fill(new FluidStack(recipe.getFluid(), amount),
                        IFluidHandler.FluidAction.SIMULATE) == amount;
            }
        }
        return false;
    }

    private int feedSlot(IItemHandler inventory, IPressingTub tub) {
        // Refill an exhausted basin; do not top up every tick while it is being pressed.
        if (!tub.getItems().getStackInSlot(0).isEmpty()) return -1;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack fruit = inventory.extractItem(i, 1, true);
            if (canPress(tub, fruit) && tub.getItems().insertItem(0, fruit, true).isEmpty()) return i;
        }
        return -1;
    }

    private void feed(EntityMaid maid, IPressingTub tub) {
        IItemHandler inventory = workInventory(maid);
        int slot = feedSlot(inventory, tub);
        if (slot < 0) return;
        ItemStack offered = inventory.extractItem(slot, tub.getItems().getSlotLimit(0), true);
        int accepted = offered.getCount() - tub.getItems().insertItem(0, offered, true).getCount();
        if (accepted <= 0) return;
        ItemStack fruit = inventory.extractItem(slot, accepted, false);
        // Tavern shrinks this stack by the accepted amount and refreshes its block entity.
        tub.addIngredient(fruit);
        returnToBackpack(maid, inventory, fruit);
        maid.swing(InteractionHand.MAIN_HAND);
    }

    private int bucketSlot(IItemHandler inventory, IPressingTub tub) {
        FluidActionResult fill = FluidUtil.tryFillContainer(new ItemStack(Items.BUCKET), tub.getFluid(), BUCKET, null, false);
        if (!fill.isSuccess()) return -1;
        for (int i = 0; i < inventory.getSlots(); i++) {
            if (!inventory.extractItem(i, 1, true).is(Items.BUCKET)) continue;
            if (canStoreAfterBucket(inventory, fill.getResult(), i)) return i;
        }
        return -1;
    }

    private boolean canStoreAfterBucket(IItemHandler inventory, ItemStack result, int bucketSlot) {
        if (ItemHandlerHelper.insertItemStacked(inventory, result, true).isEmpty()) return true;
        // A single empty bucket frees its own slot, even in an otherwise full backpack.
        return inventory.getStackInSlot(bucketSlot).getCount() == 1
                && inventory.isItemValid(bucketSlot, result)
                && Math.min(inventory.getSlotLimit(bucketSlot), result.getMaxStackSize()) >= result.getCount();
    }

    private void collect(EntityMaid maid, IPressingTub tub) {
        IItemHandler inventory = workInventory(maid);
        int slot = bucketSlot(inventory, tub);
        if (slot < 0) return;
        ItemStack empty = inventory.extractItem(slot, 1, false);
        if (empty.isEmpty()) return;
        FluidActionResult preview = FluidUtil.tryFillContainer(empty, tub.getFluid(), BUCKET, null, false);
        if (!preview.isSuccess() || !ItemHandlerHelper.insertItemStacked(inventory, preview.getResult(), true).isEmpty()) {
            returnToBackpack(maid, inventory, empty);
            return;
        }
        FluidActionResult filled = FluidUtil.tryFillContainer(empty, tub.getFluid(), BUCKET, null, true);
        returnToBackpack(maid, inventory, filled.isSuccess() ? filled.getResult() : empty);
        if (filled.isSuccess()) maid.swing(InteractionHand.MAIN_HAND);
    }

    /** Backpack first for results, but also accept supplies equipped in either hand. */
    private IItemHandler workInventory(EntityMaid maid) {
        return new CombinedInvWrapper(maid.getAvailableBackpackInv(), maid.getHandsInvWrapper());
    }

    private void returnToBackpack(EntityMaid maid, IItemHandler inventory, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, stack, false);
        // Unexpected inventory rejection must never delete the item.
        if (!remainder.isEmpty()) maid.spawnAtLocation(remainder);
    }
}
