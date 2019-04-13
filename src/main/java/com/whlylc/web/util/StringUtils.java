/*
 * Copyright (c) 2015, All rights reserved.
 */
package com.whlylc.web.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * String utility functions
 *
 * @author Zeal 2015-12-14
 */
public class StringUtils extends org.apache.commons.lang3.StringUtils {

    /**
     * Generate UUID
     */
    public static String generateUUID() {
        return StringUtils.remove(UUID.randomUUID().toString(), '-');
    }

    /**
     * Convenience method to return a String array as a delimited (e.g. CSV)
     * String. E.g. useful for {@code toString()} implementations.
     *
     * @param arr   the array to display
     * @param delim the delimiter to use (probably a ",")
     * @return the delimited String
     */
    public static String arrayToDelimitedString(Object[] arr, String delim) {
        if (arr == null || arr.length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) {
                sb.append(delim);
            }
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    /**
     * Convenience method to return a Collection as a delimited (e.g. CSV)
     * String. E.g. useful for {@code toString()} implementations.
     *
     * @param coll  the Collection to display
     * @param delimiter the delimiter to use (probably a ",")
     * @return the delimited String
     */
    public static String collectionToDelimitedString(Collection<?> coll, String delimiter) {
        return collectionToDelimitedString(coll, delimiter, "", "");
    }


    /**
     * Convenience method to return a Collection as a delimited (e.g. CSV)
     * String. E.g. useful for {@code toString()} implementations.
     *
     * @param coll   the Collection to display
     * @param delim  the delimiter to use (probably a ",")
     * @param prefix the String to start each element with
     * @param suffix the String to end each element with
     * @return the delimited String
     */
    public static String collectionToDelimitedString(Collection<?> coll, String delim, String prefix, String suffix) {
        if (coll == null || coll.size() <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = coll.iterator();
        while (it.hasNext()) {
            sb.append(prefix).append(it.next()).append(suffix);
            if (it.hasNext()) {
                sb.append(delim);
            }
        }
        return sb.toString();
    }

    /**
     * Tokenize the given String into a String array via a StringTokenizer.
     * Trims tokens and omits empty tokens.
     * <p>The given delimiters string is supposed to consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character; for multi-character
     * delimiters, consider using {@code delimitedListToStringArray}
     *
     * @param str        the String to tokenize
     * @param delimiters the delimiter characters, assembled as String
     *                   (each of those characters is individually considered as delimiter).
     * @return an array of the tokens
     * @see StringTokenizer
     * @see String#trim()
     */
    public static List<String> tokenizeToStringList(String str, String delimiters) {
        return tokenizeToStringList(str, delimiters, true, true);
    }

    /**
     * Tokenize the given String into a String array via a StringTokenizer.
     * <p>The given delimiters string is supposed to consist of any number of
     * delimiter characters. Each of those characters can be used to separate
     * tokens. A delimiter is always a single character; for multi-character
     * delimiters, consider using {@code delimitedListToStringArray}
     *
     * @param str               the String to tokenize
     * @param delimiters        the delimiter characters, assembled as String
     *                          (each of those characters is individually considered as delimiter)
     * @param trimTokens        trim the tokens via String's {@code trim}
     * @param ignoreEmptyTokens omit empty tokens from the result array
     *                          (only applies to tokens that are empty after trimming; StringTokenizer
     *                          will not consider subsequent delimiters as token in the first place).
     * @return an array of the tokens ({@code null} if the input String
     * was {@code null})
     * @see StringTokenizer
     * @see String#trim()
     */
    public static List<String> tokenizeToStringList(
            String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens) {

        if (str == null) {
            return new ArrayList<>(0);
        }
        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<String>();
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            if (trimTokens) {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    public static void main(String[] args) throws Exception {
        String css = "background-image: url(../img/video.png)";
        css = "background-image: url('../img/video.png)";
        css = "background: #FAFAFA url('http://i.imgur.com/R60qKwS.jpg')";
        Pattern CSS_URL_PATTERN = Pattern.compile("url\\([\\s]*['\"]?((?!['\"]?https?://|['\"]?data:|['\"]?/).*?)['\"]?[\\s]*\\)");
//        Pattern CSS_URL_PATTERN = Pattern.compile("url\\([\\s]*['\"]?((?!https?://|data:|/).*?)['\"]?[\\s]*\\)");
//        Pattern CSS_URL_PATTERN = Pattern.compile("url\\([\\s]*['\"]?((?!https?://|data:|/).*)['\"]?[\\s]*\\)");
        Matcher matcher = CSS_URL_PATTERN.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String url = matcher.group(1).trim();
            matcher.appendReplacement(sb, "*" + url);
        }
        matcher.appendTail(sb);
        System.out.println(sb.toString());
    }

}
