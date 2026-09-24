package com.adipginting.lecturo.domain.skills

/**
 * The skills that ship with the app, so the loop is usable before any catalog is
 * configured. They are ordinary skills: a catalog that installs the same id
 * replaces one, and they can be uninstalled like any other.
 */
val STARTER_SKILLS = listOf(
    Skill(
        id = "starter.summary",
        name = "Summarise this book",
        description = "A short summary of the text you are reading.",
        author = "Lecturo",
        version = "1",
        scope = SCOPE_BOOK,
        trigger = TRIGGER_MANUAL,
        template = "Summarise the following text in a few plain paragraphs. " +
            "Say what it is about and what matters in it.\n\n{{book}}",
    ),
    Skill(
        id = "starter.notes",
        name = "Study notes",
        description = "Turn the text into notes worth revising from.",
        author = "Lecturo",
        version = "1",
        scope = SCOPE_BOOK,
        trigger = TRIGGER_MANUAL,
        template = "Turn the following text into study notes: short headed sections, " +
            "then bullets of the points a reader should remember. Keep the author's " +
            "meaning, drop the filler.\n\n{{book}}",
    ),
    Skill(
        id = "starter.explain",
        name = "Explain the passage",
        description = "Explain a selected passage in plain language.",
        author = "Lecturo",
        version = "1",
        scope = SCOPE_MESSAGE,
        trigger = TRIGGER_MANUAL,
        template = "Explain the following passage in plain language, assuming the " +
            "reader is new to the subject. Then say why it matters in the text around it." +
            "\n\n{{excerpt}}",
    ),
)
