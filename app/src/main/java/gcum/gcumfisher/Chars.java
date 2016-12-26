package gcum.gcumfisher;

import android.support.annotation.NonNull;

public class Chars {

    public static char toStdChar(char a) {
        switch (a) {
            case 'à':
            case 'â':
            case 'ä':
                return 'a';
            case 'é':
            case 'è':
            case 'ê':
            case 'ë':
                return 'e';
            case 'ï':
            case 'î':
                return 'i';
            case 'ö':
            case 'ô':
                return 'o';
            case 'ù':
            case 'û':
            case 'ü':
                return 'u';
            case 'ç':
                return 'c';
            case 'œ':
                return 'o';
            case 'À':
            case 'Â':
            case 'Ä':
                return 'A';
            case 'É':
            case 'È':
            case 'Ê':
            case 'Ë':
                return 'E';
            case 'Ï':
            case 'Î':
                return 'I';
            case 'Ö':
            case 'Ô':
                return 'O';
            case 'Ù':
            case 'Û':
            case 'Ü':
                return 'U';
            case 'Ç':
                return 'C';
            case 'Œ':
                return 'O';
            default:
                int c = (int) a;
                if ((c >= 32) && (c < 127)) return a;
                else return '?';
        }
    }
    public static String toStdChars(char a) {
        switch (a) {
            case 'œ':
                return "oe";
            case 'Œ':
                return "Oe";
            default:
                return String.valueOf(toStdChar(a));
        }
    }

    @NonNull
    static String toStdChars(@NonNull CharSequence source) {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < source.length(); i++) s.append(toStdChars(source.charAt(i)));
        return s.toString();
    }

}
