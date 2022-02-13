package co.featureflags.server;

import co.featureflags.server.exterior.JsonParseException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

abstract class JsonHelper {
    private static final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();

    private JsonHelper() {
        super();
    }


    static <T> T deserialize(String json, Class<T> objectClass) throws JsonParseException {
        try {
            return gson.fromJson(json, objectClass);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }

    static <T> T deserialize(String json, Type type) throws JsonParseException {
        try {
            return gson.fromJson(json, type);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }

    static <T> T deserialize(Reader reader, Class<T> objectClass) throws JsonParseException {
        try {
            return gson.fromJson(reader, objectClass);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }

    static <T> T deserialize(Reader reader, Type type) throws JsonParseException {
        try {
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            throw new JsonParseException(e);
        }
    }

    static String serialize(Object o) {
        return gson.toJson(o);
    }

    interface AfterJsonParseDeserializable {
        void afterDeserialization();
    }

    static final class AfterJsonParseDeserializableTypeAdapterFactory implements TypeAdapterFactory {
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            return new AfterJsonParseDeserializableTypeAdapter(gson.getDelegateAdapter(this, typeToken));
        }
    }

    static final class AfterJsonParseDeserializableTypeAdapter<T> extends TypeAdapter<T> {
        private final TypeAdapter<T> typeAdapter;

        public AfterJsonParseDeserializableTypeAdapter(TypeAdapter<T> typeAdapter) {
            this.typeAdapter = typeAdapter;
        }

        @Override
        public void write(JsonWriter jsonWriter, T t) throws IOException {
            typeAdapter.write(jsonWriter, t);
        }

        @Override
        public T read(JsonReader jsonReader) throws IOException {
            T res = typeAdapter.read(jsonReader);
            if (res instanceof AfterJsonParseDeserializable) {
                ((AfterJsonParseDeserializable) res).afterDeserialization();
            }
            return res;
        }
    }

}
