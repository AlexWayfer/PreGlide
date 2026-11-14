package name.alexwayfer.preglide.engine.suggest;

import java.util.ArrayList;

/** Models the missing List API on Android before API 35, even on a newer test JVM. */
public class LegacyOccurrences extends ArrayList<WordOccurrence> {
    @Override
    public WordOccurrence removeLast() {
        throw new NoSuchMethodError("List.removeLast is unavailable before Android 15");
    }
}
