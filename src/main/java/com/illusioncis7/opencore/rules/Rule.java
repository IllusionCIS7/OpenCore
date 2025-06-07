package com.illusioncis7.opencore.rules;

public class Rule {
    public final int id;
    public final String text;
    /** Category of the rule such as Verhalten, Gameplay or Technik. */
    public final String category;

    public Rule(int id, String text, String category) {
        this.id = id;
        this.text = text;
        this.category = category;
    }
}
