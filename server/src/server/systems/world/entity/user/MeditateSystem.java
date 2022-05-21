package server.systems.world.entity.user;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IntervalIteratingSystem;
import component.console.ConsoleMessage;
import component.entity.character.states.Meditating;
import component.entity.character.status.Mana;
import component.entity.world.CombatMessage;
import component.graphic.Effect;
import server.systems.network.EntityUpdateSystem;
import server.systems.network.MessageSystem;
import server.systems.world.WorldEntitiesSystem;
import server.systems.world.entity.factory.EffectEntitySystem;
import server.systems.world.entity.factory.SoundEntitySystem;
import server.utils.UpdateTo;
import shared.interfaces.Constants;
import shared.util.EntityUpdateBuilder;
import shared.util.Messages;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

// @todo ¿Los efectos de sonido y animaciones no podría generarlos el cliente?
public class MeditateSystem extends IntervalIteratingSystem {

    private static final float MANA_RECOVERY_PERCENT = 0.1f;
    private static Map<Integer, Integer> userMeditations = new HashMap<>();
    private WorldEntitiesSystem worldEntitiesSystem;
    private EntityUpdateSystem entityUpdateSystem;
    private MessageSystem messageSystem;
    private SoundEntitySystem soundEntitySystem;
    private EffectEntitySystem effectEntitySystem;

    ComponentMapper<Mana> mMana;
    ComponentMapper<Meditating> mMeditating;

    public MeditateSystem(float timer) {
        super(Aspect.all(Meditating.class, Mana.class), timer);
    }

    @Override
    protected void process(int entityId) {
        Mana mana = mMana.get(entityId);
        EntityUpdateBuilder update = EntityUpdateBuilder.of(entityId);
        EntityUpdateBuilder notify = EntityUpdateBuilder.of(entityId);
        if (mana.min < mana.max) {
            int manaMin = mana.min;
            int prob = ThreadLocalRandom.current().nextInt(2);
            if (prob == 1) {
                // meditar
                mana.min += mana.max * MANA_RECOVERY_PERCENT;
                mana.min = Math.min(mana.min, mana.max);
                int recoveredMana = mana.min - manaMin;

                CombatMessage manaMessage = CombatMessage.magic("+" + recoveredMana);
                notify.withComponents(manaMessage);

                update.withComponents(mana);

                // send console message
                ConsoleMessage consoleMessage = ConsoleMessage.info(Messages.MANA_RECOVERED.name(), Integer.toString(recoveredMana));
                messageSystem.add(entityId, consoleMessage);
            }
        }

        if (mana.min >= mana.max) {
            notify.remove(Meditating.class);
            ConsoleMessage consoleMessage = ConsoleMessage.info(Messages.MEDITATE_STOP.name());
            messageSystem.add(entityId, consoleMessage);
            stopMeditationEffect(entityId);
        }

        if (!update.isEmpty()) {
            entityUpdateSystem.add(update.build(), UpdateTo.ENTITY);
        }

        if (!notify.isEmpty()) {
            entityUpdateSystem.add(notify.build(), UpdateTo.ALL);
        }
    }

    public void toggle(int entityId) {
        ConsoleMessage consoleMessage;
        EntityUpdateBuilder update = EntityUpdateBuilder.of(entityId);

        if (mMeditating.has(entityId)) {
            stopMeditationEffect(entityId);
            consoleMessage = ConsoleMessage.info(Messages.MEDITATE_STOP.name());
            update.remove(Meditating.class);
        } else {
            Mana mana = mMana.get(entityId); // @todo Chequear que la entidad pueda meditar
            if (mana != null && mana.min == mana.max) {
                consoleMessage = ConsoleMessage.info(Messages.MANA_FULL.name());
            } else {
                effectEntitySystem.addEffect(entityId, Constants.MEDITATE_NW_FX, Effect.LOOP_INFINITE);
                soundEntitySystem.add(entityId, 18, true);
                Meditating meditating = mMeditating.create(entityId);
                consoleMessage = ConsoleMessage.info(Messages.MEDITATE_START.name());
                update.withComponents(meditating);
            }
        }
        messageSystem.add(entityId, consoleMessage);
        entityUpdateSystem.add(update.build(), UpdateTo.ALL);
    }

    private void stopMeditationEffect(int entityId) {
        effectEntitySystem.removeEffect(entityId, Constants.MEDITATE_NW_FX);
        soundEntitySystem.remove(entityId, 18);
        mMeditating.remove(entityId);
    }

}
