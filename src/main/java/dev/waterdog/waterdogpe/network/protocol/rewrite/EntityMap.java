/*
 * Copyright 2022 WaterdogTEAM
 * Licensed under the GNU General Public License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.waterdog.waterdogpe.network.protocol.rewrite;

import dev.waterdog.waterdogpe.network.protocol.rewrite.types.RewriteData;
import dev.waterdog.waterdogpe.network.protocol.user.PlayerRewriteUtils;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import it.unimi.dsi.fastutil.longs.LongListIterator;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataMap;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataType;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorDataTypes;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorLink;
import org.cloudburstmc.protocol.bedrock.data.camera.CameraAttachToEntityInstruction;
import org.cloudburstmc.protocol.bedrock.data.payload.list.PlayerListAddEntry;
import org.cloudburstmc.protocol.bedrock.data.payload.list.PlayerListEntry;
import org.cloudburstmc.protocol.bedrock.data.payload.shape.PrimitiveShapeDataPayload;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.Arrays;
import java.util.Collection;
import java.util.ListIterator;

import static dev.waterdog.waterdogpe.network.protocol.Signals.mergeSignals;

/**
 * Class to map the proper entityIds to entity-related packets.
 */
public class EntityMap implements BedrockPacketHandler {
    private static final Collection<ActorDataType<Long>> ENTITY_DATA_FIELDS = Arrays.asList(
            ActorDataTypes.OWNER,
            ActorDataTypes.TARGET,
            ActorDataTypes.LEASH_HOLDER,
            ActorDataTypes.TARGET_A,
            ActorDataTypes.TARGET_B,
            ActorDataTypes.TARGET_C,
            ActorDataTypes.TRADE_TARGET,
            ActorDataTypes.BALLOON_ANCHOR,
            ActorDataTypes.AGENT
    );

    private final ProxiedPlayer player;
    private final RewriteData data;

    public EntityMap(ProxiedPlayer player) {
        this.player = player;
        this.data = player.getRewriteData();
    }

    public PacketSignal doRewrite(BedrockPacket packet) {
        return this.player.canRewrite() ? packet.handle(this) : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(MoveActorAbsolutePacket packet) {
        return data.rewriteEntityId(packet.getMoveData().getActorRuntimeID(), value -> packet.getMoveData().setActorRuntimeID(value));
    }

    @Override
    public PacketSignal handle(ActorEventPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(MobEffectPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(UpdateAttributesPacket packet) {
        return data.rewriteEntityId(packet.getRuntimeID(), packet::setRuntimeID);
    }

    @Override
    public PacketSignal handle(MobEquipmentPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(MobArmorEquipmentPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(PlayerActionPacket packet) {
        return data.rewriteEntityId(packet.getPlayerRuntimeID(), packet::setPlayerRuntimeID);
    }

    @Override
    public PacketSignal handle(SetActorDataPacket packet) {
        PacketSignal signal = data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
        PacketSignal metaSignal = this.rewriteMetadata(packet.getActorData());
        return mergeSignals(signal, metaSignal);
    }

    @Override
    public PacketSignal handle(SetActorMotionPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(MoveActorDeltaPacket packet) {
        return data.rewriteEntityId(packet.getMoveData().getActorRuntimeID(), value -> packet.getMoveData().setActorRuntimeID(value));
    }

    @Override
    public PacketSignal handle(SetLocalPlayerAsInitializedPacket packet) {
        return data.rewriteEntityId(packet.getPlayerID(), packet::setPlayerID);
    }

    @Override
    public PacketSignal handle(AddPlayerPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);

        PacketSignal signal2 = PacketSignal.UNHANDLED;

        ListIterator<ActorLink> iterator = packet.getActorLinks().listIterator();
        while (iterator.hasNext()) {
            ActorLink entityLink = iterator.next();
            long from = PlayerRewriteUtils.rewriteId(entityLink.getTargetA(), this.data.getEntityId(), this.data.getOriginalEntityId());
            long to = PlayerRewriteUtils.rewriteId(entityLink.getTargetB(), this.data.getEntityId(), this.data.getOriginalEntityId());
            if (entityLink.getTargetA() != from || entityLink.getTargetB() != to) {
                iterator.set(new ActorLink(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isPassengerInitiated()));
                signal2 = PacketSignal.HANDLED;
            }
        }

        PacketSignal signal3 = this.rewriteMetadata(packet.getActorData());
        return (signal0 == PacketSignal.HANDLED || signal1 == PacketSignal.HANDLED || signal2 == PacketSignal.HANDLED || signal3 == PacketSignal.HANDLED) ?
                PacketSignal.HANDLED : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddActorPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);

        PacketSignal signal2 = PacketSignal.UNHANDLED;

        ListIterator<ActorLink> iterator = packet.getActorLinks().listIterator();
        while (iterator.hasNext()) {
            ActorLink entityLink = iterator.next();
            long from = PlayerRewriteUtils.rewriteId(entityLink.getTargetA(), this.data.getEntityId(), this.data.getOriginalEntityId());
            long to = PlayerRewriteUtils.rewriteId(entityLink.getTargetB(), this.data.getEntityId(), this.data.getOriginalEntityId());
            if (entityLink.getTargetA() != from || entityLink.getTargetB() != to) {
                iterator.set(new ActorLink(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isPassengerInitiated()));
                signal2 = PacketSignal.HANDLED;
            }
        }

        PacketSignal signal4 = this.rewriteMetadata(packet.getActorData());
        return (signal0 == PacketSignal.HANDLED || signal1 == PacketSignal.HANDLED || signal2 == PacketSignal.HANDLED || signal4 == PacketSignal.HANDLED) ?
                PacketSignal.HANDLED : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddItemActorPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);
        PacketSignal signal2 = this.rewriteMetadata(packet.getEntityData());
        return (signal0 == PacketSignal.HANDLED || signal1 == PacketSignal.HANDLED || signal2 == PacketSignal.HANDLED) ?
                PacketSignal.HANDLED : PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddPaintingPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(RemoveActorPacket packet) {
        return data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);
    }

    @Override
    public PacketSignal handle(BossEventPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getPlayerID(), packet::setPlayerID);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(TakeItemActorPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getActorRuntimeID(), packet::setActorRuntimeID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getItemRuntimeID(), packet::setItemRuntimeID);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(MovePlayerPacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getPlayerRuntimeID(), packet::setPlayerRuntimeID);
        PacketSignal signal1 = data.rewriteEntityId(packet.getRidingRuntimeID(), packet::setRidingRuntimeID);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(InteractPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(PlayerLocationPacket packet) {
        return data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);
    }

    @Override
    public PacketSignal handle(SetActorLinkPacket packet) {
        ActorLink entityLink = packet.getLink();
        long from = PlayerRewriteUtils.rewriteId(entityLink.getTargetA(), this.data.getEntityId(), this.data.getOriginalEntityId());
        long to = PlayerRewriteUtils.rewriteId(entityLink.getTargetA(), this.data.getEntityId(), this.data.getOriginalEntityId());

        if (from != entityLink.getTargetA() || to != entityLink.getTargetB()) {
            packet.setLink(new ActorLink(from, to, entityLink.getType(), entityLink.isImmediate(), entityLink.isPassengerInitiated()));
            return PacketSignal.HANDLED;
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AnimatePacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(AdventureSettingsPacket packet) {
        return data.rewriteEntityId(packet.getUniqueEntityId(), packet::setUniqueEntityId);
    }

    @Override
    public PacketSignal handle(PlayerListPacket packet) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        for (PlayerListEntry listEntry : packet.getEntries()) {
            if (listEntry instanceof PlayerListAddEntry entry) {
                long rewriteId = PlayerRewriteUtils.rewriteId(entry.getActorUniqueID(), this.data.getEntityId(), this.data.getOriginalEntityId());
                if (rewriteId != entry.getActorUniqueID()) {
                    signal = PacketSignal.HANDLED;
                    entry.setActorUniqueID(rewriteId);
                }
            }
        }
        return signal;
    }

    @Override
    public PacketSignal handle(UpdateTradePacket packet) {
        PacketSignal signal0 = data.rewriteEntityId(packet.getEntityUniqueId(), packet::setEntityUniqueId);
        PacketSignal signal1 = data.rewriteEntityId(packet.getLastTradingPlayer(), packet::setLastTradingPlayer);
        return mergeSignals(signal0, signal1);
    }

    @Override
    public PacketSignal handle(RespawnPacket packet) {
        return data.rewriteEntityId(packet.getPlayerRuntimeId(), packet::setPlayerRuntimeId);
    }

    @Override
    public PacketSignal handle(EmoteListPacket packet) {
        return data.rewriteEntityId(packet.getRuntimeId(), packet::setRuntimeId);
    }

    public PacketSignal handle(NpcDialoguePacket packet) {
        return data.rewriteEntityId(packet.getNpcId(), packet::setNpcId);
    }

    public PacketSignal handle(NpcRequestPacket packet) {
        return data.rewriteEntityId(packet.getNpcRuntimeID(), packet::setNpcRuntimeID);
    }

    @Override
    public PacketSignal handle(EmotePacket packet) {
        return data.rewriteEntityId(packet.getActorRuntimeId(), packet::setActorRuntimeId);
    }

    @Override
    public PacketSignal handle(SpawnParticleEffectPacket packet) {
        return data.rewriteEntityId(packet.getActorId(), packet::setActorId);
    }

    @Override
    public PacketSignal handle(ActorPickRequestPacket packet) {
        return data.rewriteEntityId(packet.getActorID(), packet::setActorID);
    }

    @Override
    public PacketSignal handle(LegacyTelemetryEventPacket packet) {
        return data.rewriteEntityId(packet.getTargetActorID(), packet::setTargetActorID);
    }

    @Override
    public PacketSignal handle(UpdatePlayerGameTypePacket packet) {
        return data.rewriteEntityId(packet.getTargetPlayer(), packet::setTargetPlayer);
    }

    @Override
    public PacketSignal handle(UpdateAbilitiesPacket packet) {
        return data.rewriteEntityId(packet.getData().getTargetPlayerRawId(), value -> packet.getData().setTargetPlayerRawId(value));
    }

    @Override
    public PacketSignal handle(ClientCheatAbilityPacket packet) {
        return data.rewriteEntityId(packet.getData().getTargetPlayerRawId(), value -> packet.getData().setTargetPlayerRawId(value));
    }

    @Override
    public PacketSignal handle(PlayerUpdateEntityOverridesPacket packet) {
        return data.rewriteEntityId(packet.getTargetID(), packet::setTargetID);
    }

    @Override
    public PacketSignal handle(LevelSoundEventPacket packet) {
        return data.rewriteEntityId(packet.getActorUniqueId(), packet::setActorUniqueId);
    }

    @Override
    public PacketSignal handle(AnimateEntityPacket packet) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        LongListIterator iterator = packet.getRuntimeIds().listIterator();
        while (iterator.hasNext()) {
            PacketSignal returnedSignal = data.rewriteEntityId(iterator.nextLong(), iterator::set);
            signal = mergeSignals(signal, returnedSignal);
        }
        return signal;
    }

    @Override
    public PacketSignal handle(MovementEffectPacket packet) {
        return data.rewriteEntityId(packet.getTargetRuntimeID(), packet::setTargetRuntimeID);
    }

    @Override
    public PacketSignal handle(ClientMovementPredictionSyncPacket packet) {
        return data.rewriteEntityId(packet.getActorID(), packet::setActorID);
    }

    @Override
    public PacketSignal handle(UpdateEquipPacket packet) {
        return data.rewriteEntityId(packet.getEntityUniqueId(), packet::setEntityUniqueId);
    }

    @Override
    public PacketSignal handle(CameraInstructionPacket packet) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        CameraAttachToEntityInstruction attachInstruction = packet.getAttachToEntityInstruction();
        if (attachInstruction != null) {
            PacketSignal returnedSignal = data.rewriteEntityId(attachInstruction.getEntityActorID(), attachInstruction::setEntityActorID);
            signal = mergeSignals(signal, returnedSignal);
        }
        return signal;
    }

    @Override
    public PacketSignal handle(PrimitiveShapesPacket packet) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        for (PrimitiveShapeDataPayload shape : packet.getShapes()) {
            if (shape.getShapeType() != null) {
                data.getDebugShapes().put(shape.getNetworkId(), shape);
            }
            Long attachedEntityId = shape.getAttachedToEntityID();
            if (attachedEntityId != null) {
                PacketSignal returnedSignal = data.rewriteEntityId(attachedEntityId, shape::setAttachedToEntityID);
                signal = mergeSignals(signal, returnedSignal);
            }
        }
        return signal;
    }

    private PacketSignal rewriteMetadata(ActorDataMap metadata) {
        PacketSignal signal = PacketSignal.UNHANDLED;
        for (ActorDataType<Long> data : ENTITY_DATA_FIELDS) {
            Long id = metadata.get(data);
            if (id != null) {
                long rewriteId = PlayerRewriteUtils.rewriteId(id, this.data.getEntityId(), this.data.getOriginalEntityId());
                if (rewriteId != id) {
                    metadata.put(data, rewriteId);
                    signal = PacketSignal.HANDLED;
                }
            }
        }
        return signal;
    }
}
