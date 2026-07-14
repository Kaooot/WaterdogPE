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

import dev.waterdog.waterdogpe.network.protocol.user.PlayerRewriteUtils;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import org.cloudburstmc.protocol.bedrock.data.ActorLinkType;
import org.cloudburstmc.protocol.bedrock.data.HudVisibility;
import org.cloudburstmc.protocol.bedrock.data.PlayerListPacketType;
import org.cloudburstmc.protocol.bedrock.data.ScoreInfo;
import org.cloudburstmc.protocol.bedrock.data.actor.ActorLink;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.util.List;

/**
 * Pipeline Handler used to track entities of any kind, aswell as other data
 * that will be required to get removed when switching servers.
 */
public class EntityTracker implements BedrockPacketHandler {

    private final ProxiedPlayer player;

    public EntityTracker(ProxiedPlayer player) {
        this.player = player;
    }

    public PacketSignal trackEntity(BedrockPacket packet) {
        return this.handlePacket(packet);
    }

    @Override
    public PacketSignal handle(AddPlayerPacket packet) {
        this.player.getEntities().add(packet.getTargetActorID());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddActorPacket packet) {
        this.player.getEntities().add(packet.getTargetActorID());
        for (ActorLink actorLink : packet.getActorLinks()) {
            this.handleEntityLink(actorLink);
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddItemActorPacket packet) {
        this.player.getEntities().add(packet.getTargetActorID());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddPaintingPacket packet) {
        this.player.getEntities().add(packet.getTargetActorID());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(RemoveActorPacket packet) {
        this.player.getEntities().remove(packet.getTargetActorID());
        // TODO Check if this is correct
        this.player.getEntityLinks().remove(packet.getTargetActorID());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(AddVolumeEntityPacket packet) {
        this.player.getVolumeEntities().put(packet.getEntityNetworkId(), packet.getDimensionType().getValue());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(RemoveVolumeEntityPacket packet) {
        this.player.getVolumeEntities().remove(packet.getEntityNetworkId());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(PlayerFogPacket packet) {
        this.player.setFogApplied(!packet.getFogStack().isEmpty());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(UpdateClientInputLocksPacket packet) {
        this.player.getInputLockData().addAll(packet.getInputLockComponents());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(SetHudPacket packet) {
        if (packet.getHudVisible() == HudVisibility.HIDE) {
            this.player.getHiddenHudElements().addAll(packet.getHudElementList());
        } else {
            this.player.getHiddenHudElements().removeAll(packet.getHudElementList());
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(ContainerOpenPacket packet) {
        this.player.getOpenContainers().put(packet.getContainerID(), packet.getContainerType());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(ContainerClosePacket packet) {
        this.player.getOpenContainers().remove(packet.getContainerID());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(PlayerListPacket packet) {
        List<PlayerListPacket.Entry> entries = packet.getEntries();
        for (PlayerListPacket.Entry entry : entries) {
            if (packet.getAction() == PlayerListPacketType.ADD) {
                this.player.getPlayers().add(entry.getUuid());
            } else if (packet.getAction() == PlayerListPacketType.REMOVE) {
                this.player.getPlayers().remove(entry.getUuid());
            }
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public PacketSignal handle(SetActorLinkPacket packet) {
        this.handleEntityLink(packet.getLink());
        return PacketSignal.UNHANDLED;
    }

    private void handleEntityLink(ActorLink actorLink) {
        if (actorLink.getType() == ActorLinkType.NONE) {
            this.player.getEntityLinks().remove(actorLink.getTargetA());
        } else {
            this.player.getEntityLinks().put(actorLink.getTargetA(), actorLink.getTargetB());
        }
    }

    @Override
    public PacketSignal handle(SetActorDataPacket packet) {
        if (packet.getTargetRuntimeID() == this.player.getRewriteData().getOriginalEntityId()) {
            boolean immobile = PlayerRewriteUtils.checkForImmobileFlag(packet.getActorData());
            this.player.getRewriteData().setImmobileFlag(immobile);
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public final PacketSignal handle(SetDisplayObjectivePacket packet) {
        this.player.getScoreboards().add(packet.getObjectiveName());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public final PacketSignal handle(RemoveObjectivePacket packet) {
        this.player.getScoreboards().remove(packet.getObjectiveName());
        return PacketSignal.UNHANDLED;
    }

    @Override
    public final PacketSignal handle(SetScorePacket packet) {
        switch(packet.getScorePacketType()) {
            case SET:
                for(ScoreInfo info : packet.getScoreInfo()) {
                    this.player.getScoreInfos().put(info.getScoreboardId(), info);
                }
                break;
            case REMOVE:
                for(ScoreInfo info : packet.getScoreInfo()) {
                    this.player.getScoreInfos().remove(info.getScoreboardId());
                }
                break;
        }
        return PacketSignal.UNHANDLED;
    }

    @Override
    public final PacketSignal handle(BossEventPacket packet) {
        switch (packet.getEventType()) {
            case ADD -> this.player.getBossbars().add(packet.getTargetActorID());
            case REMOVE -> this.player.getBossbars().remove(packet.getTargetActorID());
        }
        return PacketSignal.UNHANDLED;
    }
}
