package ret.tawny.truthful.utils.network;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DiscordWebhook {

    private final String url;
    private String username;
    private String avatarUrl;
    private final List<EmbedObject> embeds = new ArrayList<>();

    public DiscordWebhook(String url) {
        this.url = url;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public void addEmbed(EmbedObject embed) {
        this.embeds.add(embed);
    }

    public void execute() throws IOException {
        if (this.url == null || this.url.isEmpty()) return;

        JsonObject json = new JsonObject();

        if (this.username != null) json.addProperty("username", this.username);
        if (this.avatarUrl != null) json.addProperty("avatar_url", this.avatarUrl);

        if (!this.embeds.isEmpty()) {
            JsonArray embedArray = new JsonArray();
            for (EmbedObject embed : this.embeds) {
                JsonObject embedJson = new JsonObject();
                if (embed.getTitle() != null) embedJson.addProperty("title", embed.getTitle());
                if (embed.getDescription() != null) embedJson.addProperty("description", embed.getDescription());
                if (embed.getColor() != null) embedJson.addProperty("color", embed.getColor());

                if (embed.getFooter() != null) {
                    JsonObject footerJson = new JsonObject();
                    footerJson.addProperty("text", embed.getFooter());
                    embedJson.add("footer", footerJson);
                }

                if (embed.getThumbnail() != null) {
                    JsonObject thumbJson = new JsonObject();
                    thumbJson.addProperty("url", embed.getThumbnail());
                    embedJson.add("thumbnail", thumbJson);
                }

                if (!embed.getFields().isEmpty()) {
                    JsonArray fieldsArray = new JsonArray();
                    for (Field field : embed.getFields()) {
                        JsonObject fieldJson = new JsonObject();
                        fieldJson.addProperty("name", field.name);
                        fieldJson.addProperty("value", field.value);
                        fieldJson.addProperty("inline", field.inline);
                        fieldsArray.add(fieldJson);
                    }
                    embedJson.add("fields", fieldsArray);
                }
                embedArray.add(embedJson);
            }
            json.add("embeds", embedArray);
        }

        String jsonPayload = json.toString();

        URL urlObj = new URL(this.url);
        HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("User-Agent", "TruthfulAC-Webhook");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }

        connection.getInputStream().close(); // Trigger request
        connection.disconnect();
    }

    public static class EmbedObject {
        private String title;
        private String description;
        private Integer color;
        private String footer;
        private String thumbnail;
        private final List<Field> fields = new ArrayList<>();

        public EmbedObject setTitle(String title) {
            this.title = title;
            return this;
        }

        public EmbedObject setDescription(String description) {
            this.description = description;
            return this;
        }

        public EmbedObject setColor(int color) {
            this.color = color;
            return this;
        }

        public EmbedObject setFooter(String text) {
            this.footer = text;
            return this;
        }

        public EmbedObject setThumbnail(String url) {
            this.thumbnail = url;
            return this;
        }

        public EmbedObject addField(String name, String value, boolean inline) {
            this.fields.add(new Field(name, value, inline));
            return this;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public Integer getColor() { return color; }
        public String getFooter() { return footer; }
        public String getThumbnail() { return thumbnail; }
        public List<Field> getFields() { return fields; }
    }

    private static class Field {
        String name;
        String value;
        boolean inline;

        Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }
}