package mindustry.type;

import arc.struct.*;
import arc.util.*;
import arc.util.serialization.*;
import arc.util.serialization.Json.*;

import static arc.Core.*;

/** Class for storing map-specific locale bundles */
public class MapLocales extends ObjectMap<String, StringMap> implements JsonSerializable{
    private static TextFormatter formatter = new TextFormatter(null, false);

    @Override
    public void write(Json json){
        for(var entry : entries()){
            json.writeValue(entry.key, entry.value, StringMap.class, String.class);
        }
    }

    @Override
    public void read(Json json, JsonValue jsonData){
        for(JsonValue value : jsonData){
            put(value.name, json.readValue(StringMap.class, value));
        }
    }

    @Override
    public MapLocales copy(){
        MapLocales out = new MapLocales();

        for(var entry : this.entries()){
            StringMap map = new StringMap();
            map.putAll(entry.value);
            out.put(entry.key, map);
        }

        return out;
    }

    public String getProperty(String key){
        if(!containsProperty(settings.getString("locale"), key)){
            if(containsProperty("en", key)) return get("en").get(key);
            return "???" + key + "???";
        }
        return get(settings.getString("locale")).get(key);
    }

    private String getProperty(String locale, String key){
        if(!containsProperty(locale, key)){
            if(containsProperty("en", key)) return get("en").get(key);
            return "???" + key + "???";
        }
        return get(locale).get(key);
    }

    public boolean containsProperty(String key){
        return containsProperty(settings.getString("locale"), key) || containsProperty("en", key);
    }

    private boolean containsProperty(String locale, String key){
        if(!containsKey(locale)) return false;
        return get(locale).containsKey(key);
    }

    public String getFormatted(String key, Object... args){
        if(!containsProperty(settings.getString("locale"), key)){
            if(containsProperty("en", key)) return formatter.format(getProperty("en", key), args);
            return "???" + key + "???";
        }
        return formatter.format(getProperty(key), args);
    }
}
