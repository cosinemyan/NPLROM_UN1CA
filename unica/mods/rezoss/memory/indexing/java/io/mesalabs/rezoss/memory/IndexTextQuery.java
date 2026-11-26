/* SPDX-License-Identifier: GPL-3.0-or-later */
package io.mesalabs.rezoss.memory;

/** SQL contract shared by the Android cursor fallback and host SQLite checks. */
final class IndexTextQuery {
    final String selection;
    final String[] arguments;

    private IndexTextQuery(String selection, String[] arguments) {
        this.selection = selection;
        this.arguments = arguments;
    }

    static IndexTextQuery build(boolean memory, String text) {
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String[] fields = memory ? new String[]{"title", "body", "source_text", "user_note", "memo", "representative_tag"}
            : new String[]{"name", "searchable_keywords"};
        StringBuilder where = new StringBuilder(memory ? "is_locked = 0 AND (" : "(");
        String[] arguments = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) where.append(" OR ");
            where.append(fields[i]).append(" LIKE ? ESCAPE '\\'");
            arguments[i] = pattern;
        }
        return new IndexTextQuery(where.append(')').toString(), arguments);
    }
}
