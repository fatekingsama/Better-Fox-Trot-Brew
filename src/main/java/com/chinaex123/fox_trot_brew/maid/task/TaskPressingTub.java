package com.chinaex123.fox_trot_brew.maid.task;

import com.chinaex123.fox_trot_brew.FoxTrotBrew;
import com.chinaex123.fox_trot_brew.maid.behavior.MaidPressingTubBehavior;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.github.tartaricacid.touhoulittlemaid.util.SoundUtil;
import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 果盆任务实现
 * <p>
 * 为车万女仆模组添加自定义任务，使女仆能够自动寻找并操作森罗酒馆的果盆。
 * 女仆会执行跳跃动画来模拟压榨动作。
 */
public class TaskPressingTub implements IMaidTask {
    /**
     * 任务的唯一标识符
     */
    public static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(FoxTrotBrew.MOD_ID, "pressing_tub");

    @Override
    public @NotNull ResourceLocation getUid() {
        return UID;
    }

    @Override
    public @NotNull ItemStack getIcon() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("kaleidoscope_tavern", "pressing_tub")).getDefaultInstance();
    }

    @NotNull
    @Override
    public SoundEvent getAmbientSound(EntityMaid maid) {
        return SoundUtil.environmentSound(maid, InitSounds.MAID_IDLE.get(), 0.5f);
    }

    /**
     * 创建女仆的AI行为任务列表
     *
     * @param maid 女仆实体实例
     * @return 包含优先级和行为控制的配对列表，优先级5表示中等优先级
     */
    @Override
    public @NotNull List<Pair<Integer, BehaviorControl<? super EntityMaid>>> createBrainTasks(@NotNull EntityMaid maid) {
        return Lists.newArrayList(Pair.of(5, new MaidPressingTubBehavior(0.6f)));
    }

    @Override
    public @NotNull String getMaidActionSummary() {
        return "Press the tub from Kaleidoscope Tavern";
    }
}
