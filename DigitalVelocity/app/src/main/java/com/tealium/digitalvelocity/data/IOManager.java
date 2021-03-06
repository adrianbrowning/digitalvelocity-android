package com.tealium.digitalvelocity.data;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tealium.digitalvelocity.BuildConfig;
import com.tealium.digitalvelocity.data.gson.AgendaItem;
import com.tealium.digitalvelocity.data.gson.Coordinates;
import com.tealium.digitalvelocity.data.gson.Floor;
import com.tealium.digitalvelocity.data.gson.Notification;
import com.tealium.digitalvelocity.data.gson.ParseItem;
import com.tealium.digitalvelocity.data.gson.Question;
import com.tealium.digitalvelocity.data.gson.Sponsor;
import com.tealium.digitalvelocity.data.gson.Survey;
import com.tealium.digitalvelocity.event.LoadRequest;
import com.tealium.digitalvelocity.event.LoadedEvent;
import com.tealium.digitalvelocity.event.Purge;
import com.tealium.digitalvelocity.event.SaveRequest;
import com.tealium.digitalvelocity.event.ZipEvent;
import com.tealium.digitalvelocity.push.event.PushMessage;
import com.tealium.digitalvelocity.util.Constant;
import com.tealium.digitalvelocity.util.Zipper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import de.greenrobot.event.EventBus;

final class IOManager {

    private final Context mContext;
    private final Gson mGson;

    public IOManager(Context context) {
        mContext = context.getApplicationContext();
        mGson = new Gson();
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(ZipEvent event) {
        Zipper.zip(mContext.getFilesDir());
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(Purge event) {

        FileReader reader;
        ParseItem item;
        File renamed;

        for (File file : mContext.getFilesDir().listFiles(IOUtils.createPurgeFilter())) {
            // Without renaming EBUSY IO Errors can occur.
            renamed = new File(
                    file.getParentFile(),
                    System.currentTimeMillis() + "." + file.getName());
            file.renameTo(renamed);
            renamed.delete();
            if (BuildConfig.DEBUG) {
                Log.d(Constant.TAG, "Deleted " + file.getAbsolutePath());
            }
        }
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(PushMessage event) {

        Notification pushNotification = Notification.createNotification(event);

        saveFile(
                IOUtils.createNotificationFile(mContext, pushNotification.getId()),
                mGson.toJson(pushNotification));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.Survey event) {
        saveFile(
                IOUtils.createSurveyFile(mContext, event.getItem().getId()),
                mGson.toJson(event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.Question event) {
        saveFile(
                IOUtils.createQuestionFile(mContext, event.getItem().getId()),
                mGson.toJson(event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.Notification event) {
        saveFile(
                IOUtils.createNotificationFile(mContext, event.getItem().getId()),
                mGson.toJson(event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.Sponsor event) {

        final File jsonFile = IOUtils.createSponsorFile(mContext, event.getItem().getId());
        final String content = mGson.toJson(event.getItem());

        saveFile(jsonFile, content);
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.Coordinates event) {
        saveFile(
                IOUtils.createCoordsFile(mContext, event.getItem().getId()),
                mGson.toJson(event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.Floor event) {
        saveFile(
                IOUtils.createFloorFile(mContext, event.getItem().getId()),
                mGson.toJson(event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(SaveRequest.AgendaItem event) {
        saveFile(
                IOUtils.createAgendaItemFile(mContext, event.getItem().getId()),
                mGson.toJson(event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Agenda event) {
        try {
            Collection<AgendaItem> items = loadJsonFiles(AgendaItem.class, IOUtils.SUFFIX_AGENDA_ITEM);
            long latestUpdated = 0;
            for (AgendaItem item : items) {
                if (item.getUpdatedAt() > latestUpdated) {
                    latestUpdated = item.getUpdatedAt();
                }
            }

            EventBus.getDefault().post(new LoadedEvent.Agenda(items, latestUpdated));

        } catch (Throwable t) {
            Log.e(Constant.TAG, "Error processing " + event.getClass(), t);
        }
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Sponsors event) {
        EventBus.getDefault().post(new LoadedEvent.Sponsors(
                loadJsonFiles(Sponsor.class, IOUtils.SUFFIX_SPONSOR)));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Notifications event) {
        EventBus.getDefault().post(new LoadedEvent.Notifications(
                loadJsonFiles(Notification.class, IOUtils.SUFFIX_NOTIFICATION)));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Surveys event) {
        EventBus.getDefault().post(new LoadedEvent.Surveys(
                loadJsonFiles(Survey.class, IOUtils.SUFFIX_SURVEY)));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Questions event) {
        final List<Question> questions = new ArrayList<>(event.getQuestionIds().size());
        for (int i = 0; i < event.getQuestionIds().size(); i++) {
            final String id = event.getQuestionIds().get(i);
            final File file = IOUtils.createQuestionFile(mContext, id);
            try {
                questions.add(loadJsonFile(Question.class, file));
            } catch (IOException e) {
                Log.e(Constant.TAG, "! Error loading " + file.getName(), e);
            }
        }

        EventBus.getDefault().post(new LoadedEvent.Questions(questions));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Coordinates event) {
        List<Coordinates> coords = loadJsonFiles(Coordinates.class, IOUtils.SUFFIX_COORDINATES);
        Collections.sort(coords);
        EventBus.getDefault().post(new LoadedEvent.CoordinateData(coords));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.Floors event) {
        List<Floor> floors = loadJsonFiles(Floor.class, IOUtils.SUFFIX_FLOOR);
        Collections.sort(floors);
        EventBus.getDefault().post(new LoadedEvent.Floors(floors));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.SponsorLogo event) {

        File file = getLogoFile(event.getItem());

        if (!file.exists()) {
            return;
        }

        EventBus.getDefault().post(new LoadedEvent.SponsorLogo(
                BitmapFactory.decodeFile(file.getAbsolutePath()),
                event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.FloorImage event) {

        File file = IOUtils.getImageFile(mContext, event.getItem().getId());

        if (file == null) {
            return;
        }

        EventBus.getDefault().post(new LoadedEvent.FloorImage(
                BitmapFactory.decodeFile(file.getAbsolutePath()),
                event.getItem()));
    }

    @SuppressWarnings("unused")
    public void onEventBackgroundThread(LoadRequest.AgendaItemData event) {

        final File file = IOUtils.createAgendaItemFile(mContext, event.getId());
        try {
            EventBus.getDefault().post(new LoadedEvent.AgendaItemData(
                    loadJsonFile(AgendaItem.class, file)));
        } catch (IOException e) {
            Log.e(Constant.TAG, "! Error loading " + file.getName(), e);
        }
    }

    private File getLogoFile(Sponsor sponsor) {
        return new File(mContext.getFilesDir(), sponsor.getId() + ".png");
    }

    private <T extends ParseItem> List<T> loadJsonFiles(Class<T> itemsClass, String suffix) {
        List<T> loaded = new LinkedList<>();
        T item;

        for (File file : mContext.getFilesDir().listFiles()) {
            if (!file.getName().endsWith(suffix)) {
                continue;
            }
            try {
                if ((item = loadJsonFile(itemsClass, file)).isVisible()) {
                    loaded.add(item);
                }
            } catch (JsonSyntaxException | IOException e) {
                Log.e(Constant.TAG, "! Error loading " + file.getAbsolutePath(), e);
            }
        }

        return loaded;
    }

    private <T extends ParseItem> T loadJsonFile(Class<T> itemsClass, File file) throws IOException {
        FileReader reader;
        T item = mGson.fromJson(reader = new FileReader(file), itemsClass);
        reader.close();
        return item;
    }

    private static void saveFile(File file, String content) {

        FileOutputStream outputStream;

        try {
            outputStream = new FileOutputStream(file);
            outputStream.write(content.getBytes());
            outputStream.close();
        } catch (Exception e) {
            Log.e(Constant.TAG, null, e);
        }
    }
}
