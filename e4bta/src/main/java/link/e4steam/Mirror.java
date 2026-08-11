package link.e4steam;

/** Small text adapter required by the loader-independent lobby code. */
public final class Mirror {
    private Mirror() {}

    public static String translatable(String key, Object... arguments) {
        try {
            Class<?> type = Class.forName("net.minecraft.core.lang.I18n");
            Object i18n = type.getMethod("getInstance").invoke(null);
            String translated = (String) (arguments.length == 0
                    ? type.getMethod("translateKey", String.class).invoke(i18n, key)
                    : type.getMethod("translateKeyAndFormat", String.class, Object[].class)
                            .invoke(i18n, key, arguments));
            if (translated != null && !translated.equals(key)) {
                return translated;
            }
        } catch (Throwable ignored) {
        }
        if (arguments.length == 0) {
            return key;
        }
        return key + ": " + java.util.Arrays.toString(arguments);
    }
}
