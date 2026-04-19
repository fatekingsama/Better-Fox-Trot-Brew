package com.chinaex123.fox_trot_brew.maid.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidCheckRateTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidPathFindingBFS;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.github.ysbbbbbb.kaleidoscopetavern.api.blockentity.IPressingTub;
import com.google.common.collect.ImmutableMap;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;

public class MaidPressingTubBehavior extends MaidCheckRateTask {
    private final float movementSpeed;
    private BlockPos currentWorkPos;
    private int jumpCount = 0;
    private int jumpTimer = 0;
    private int restTimer = 0;

    public MaidPressingTubBehavior(float movementSpeed) {
        // 使用 REGISTERED 状态，避免与寻路阶段的内存冲突
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                InitEntities.TARGET_POS.get(), MemoryStatus.REGISTERED
        ));
        this.movementSpeed = movementSpeed;
        this.setMaxCheckRate(20);
    }

    /**
     * 检查女仆是否可以开始执行压榨桶任务。
     * <p>
     * 首先调用父类方法检查基础条件，然后寻找附近可工作的压榨桶。
     * 如果女仆已经足够接近压榨桶（水平距离小于1.2格，垂直距离小于1.5格），
     * 则清除行走记忆并正式启动行为；否则设置寻路目标让女仆靠近压榨桶。
     *
     * @param worldIn 服务器世界实例
     * @param owner   女仆实体实例
     * @return 如果女仆已到达压榨桶位置可以开始工作则返回true，否则返回false
     */
    @Override
    protected boolean checkExtraStartConditions(ServerLevel worldIn, EntityMaid owner) {
        if (super.checkExtraStartConditions(worldIn, owner)) {
            BlockPos tubPos = findTub(worldIn, owner);
            if (tubPos != null) {
                double distSq = tubPos.distToCenterSqr(owner.position());
                // 如果距离足够近，正式启动行为逻辑
                if (distSq < 1.44 && Math.abs(owner.getY() - (tubPos.getY() + 0.5)) < 1.5) {
                    this.currentWorkPos = tubPos;
                    // 清除行走记忆，防止惯性移动
                    owner.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                    return true;
                }
                // 否则，设置寻路目标
                BehaviorUtils.setWalkAndLookTargetMemories(owner, tubPos, movementSpeed, 0);
            }
        }
        return false;
    }

    /**
     * 当女仆开始执行压榨桶任务时调用，初始化所有计数器并停止导航。
     *
     * @param worldIn   服务器世界实例
     * @param maid      女仆实体实例
     * @param gameTimeIn 当前游戏时间（tick）
     */
    @Override
    protected void start(ServerLevel worldIn, EntityMaid maid, long gameTimeIn) {
        this.jumpCount = 0;
        this.jumpTimer = 0;
        this.restTimer = 0;
        maid.getNavigation().stop();
    }

    /**
     * 每tick调用，处理女仆在压榨桶前的行为逻辑。
     * <p>
     * 包括位置锁定、跳跃计数、休息控制和实际跳跃动作。
     * 女仆会连续跳跃8次，然后休息60tick（3秒），如果压榨桶仍可工作则立即继续下一轮。
     *
     * @param world    服务器世界实例
     * @param maid     女仆实体实例
     * @param gameTime 当前游戏时间（tick）
     */
    @Override
    protected void tick(ServerLevel world, EntityMaid maid, long gameTime) {
        if (currentWorkPos == null) return;

        if (world.getBlockEntity(currentWorkPos) instanceof IPressingTub tub) {
            if (tub.getFluidAmount() >= IPressingTub.MAX_FLUID_AMOUNT) {
                tryExtractWithBucket(world, maid, tub);
            }
        }

        // 强力锁定中心,防止女仆滑走
        double targetX = currentWorkPos.getX() + 0.5;
        double targetZ = currentWorkPos.getZ() + 0.5;
        double dx = targetX - maid.getX();
        double dz = targetZ - maid.getZ();
        double distSq = dx * dx + dz * dz;

        if (distSq > 0.01) {
            double factor = distSq > 0.25 ? 0.1 : 0.05;
            maid.setDeltaMovement(dx * factor, maid.getDeltaMovement().y, dz * factor);
        } else {
            maid.setDeltaMovement(0, maid.getDeltaMovement().y, 0);
        }

        // 休息阶段:跳满 8 次后进入这里
        if (jumpCount >= 8) {
            if (isWorkable(world, currentWorkPos)) {
                this.jumpCount = 0;
                this.restTimer = 0;
                this.jumpTimer = 0;
                return;
            }

            if (restTimer < 60) {
                restTimer++;
            } else {
                this.jumpCount = 0;
                this.restTimer = 0;
                this.jumpTimer = 0;
            }
            return;
        }

        // 跳跃逻辑
        if (jumpTimer > 0) {
            jumpTimer--;
            return;
        }

        if (maid.onGround()) {
            maid.setDeltaMovement(maid.getDeltaMovement().add(0.0, 0.42, 0.0));
        }
    }

    /**
     * 检查女仆是否可以继续使用当前的压榨桶任务。
     * <p>
     * 在跳跃过程中（jumpCount > 0 且 < 8）或休息期间（jumpCount >= 8 且 restTimer > 0）
     * 保持行为不中断，其他情况下需要压榨桶仍处于可工作状态。
     *
     * @param worldIn   服务器世界实例
     * @param entityIn  女仆实体实例
     * @param gameTimeIn 当前游戏时间（tick）
     * @return 如果女仆应继续执行任务则返回true，否则返回false
     */
    @Override
    protected boolean canStillUse(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        if (currentWorkPos == null) {
            return false;
        }

        double distSq = entityIn.distanceToSqr(currentWorkPos.getX() + 0.5, entityIn.getY(), currentWorkPos.getZ() + 0.5);

        if (distSq >= 2.25) {
            return false;
        }

        if (jumpCount > 0 && jumpCount < 8) {
            return true;
        }

        if (jumpCount >= 8 && restTimer > 0) {
            return true;
        }

        return isWorkable(worldIn, currentWorkPos);
    }

    /**
     * 当女仆停止执行压榨桶任务时调用，清理工作状态。
     *
     * @param worldIn   服务器世界实例
     * @param entityIn  女仆实体实例
     * @param gameTimeIn 当前游戏时间（tick）
     */
    @Override
    protected void stop(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn) {
        super.stop(worldIn, entityIn, gameTimeIn);
        this.currentWorkPos = null;
    }

    /**
     * 在女仆周围搜索可工作的压榨桶。
     * <p>
     * 使用螺旋搜索算法从女仆当前位置或限制区域中心向外扩展，
     * 优先检查垂直方向（上下4格），然后在每个水平层进行螺旋扫描。
     * 找到第一个可到达且可工作的压榨桶即返回。
     *
     * @param world 服务器世界实例
     * @param maid  女仆实体实例
     * @return 找到的压榨桶位置，如果未找到则返回null
     */
    private BlockPos findTub(ServerLevel world, EntityMaid maid) {
        MaidPathFindingBFS pathFinding = new MaidPathFindingBFS(maid.getNavigation().getNodeEvaluator(), world, maid);
        BlockPos centrePos = maid.hasRestriction() ? maid.getRestrictCenter() : maid.blockPosition();
        int searchRange = maid.hasRestriction() ? (int) maid.getRestrictRadius() : 16;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int yOffset = 0; yOffset <= 4; yOffset = yOffset > 0 ? -yOffset : 1 - yOffset) {
            for (int r = 0; r <= searchRange; r++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (Math.abs(x) != r && Math.abs(z) != r) continue;
                        mutableBlockPos.setWithOffset(centrePos, x, yOffset, z);
                        if (maid.isWithinRestriction(mutableBlockPos) &&
                                isWorkable(world, mutableBlockPos) &&
                                pathFinding.canPathReach(mutableBlockPos)) {
                            BlockPos result = mutableBlockPos.immutable();
                            pathFinding.finish();
                            return result;
                        }
                    }
                }
            }
        }
        pathFinding.finish();
        return null;
    }

    /**
     * 检查指定位置的压榨桶是否处于可工作状态。
     * <p>
     * 压榨桶可工作的条件：输入槽有物品，或者液体未满（允许继续添加液体）。
     *
     * @param world 服务器世界实例
     * @param pos   要检查的方块位置
     * @return 如果压榨桶可工作则返回true，否则返回false
     */
    private boolean isWorkable(ServerLevel world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof IPressingTub tub) {
            return !tub.getItems().getStackInSlot(0).isEmpty() ||
                    (tub.getFluidAmount() > 0 && tub.getFluidAmount() < IPressingTub.MAX_FLUID_AMOUNT);
        }
        return false;
    }

    /**
     * 尝试使用桶从压榨桶中取出液体。
     * <p>
     * 当压榨桶液体已满时,检查女仆背包中是否有空桶,如果有则使用桶右键压榨桶取出液体,
     * 并将装满液体的桶放回女仆背包。
     *
     * @param world 服务器世界实例
     * @param maid  女仆实体实例
     * @param tub   压榨桶方块实体接口
     */
    private void tryExtractWithBucket(ServerLevel world, EntityMaid maid, IPressingTub tub) {
        LazyOptional<IItemHandler> cap = maid.getCapability(ForgeCapabilities.ITEM_HANDLER);
        if (!cap.isPresent()) return;

        IItemHandler inventory = cap.orElse(null);
        if (inventory == null) return;

        int bucketSlot = -1;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.is(Items.BUCKET)) {
                bucketSlot = slot;
                break;
            }
        }

        if (bucketSlot == -1) return;

        boolean hasSpace = false;
        for (int slot = 6; slot < inventory.getSlots(); slot++) {
            if (slot == bucketSlot) {
                continue;
            }
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) {
                hasSpace = true;
                break;
            }
        }

        if (!hasSpace) {
            return;
        }

        ItemStack filledBucketTemplate = new ItemStack(Items.BUCKET);
        boolean success = tub.getResult(maid, filledBucketTemplate);

        if (success) {
            inventory.extractItem(bucketSlot, 1, false);
            ItemHandlerHelper.insertItemStacked(inventory, filledBucketTemplate, false);

            maid.swing(InteractionHand.MAIN_HAND);
            jumpCount = 0;
            restTimer = 20;
            jumpTimer = 10;
        }
    }
}