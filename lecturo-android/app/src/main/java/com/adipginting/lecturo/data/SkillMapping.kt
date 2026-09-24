package com.adipginting.lecturo.data

import com.adipginting.lecturo.domain.skills.Insight
import com.adipginting.lecturo.domain.skills.Skill

fun SkillEntity.toDomain(): Skill = Skill(
    id = id,
    name = name,
    description = description,
    author = author,
    version = version,
    scope = scope,
    trigger = trigger,
    template = template,
    price = price,
    catalogUrl = catalogUrl,
)

fun Skill.toEntity(): SkillEntity = SkillEntity(
    id = id,
    name = name,
    description = description,
    author = author,
    version = version,
    scope = scope,
    trigger = trigger,
    template = template,
    price = price,
    catalogUrl = catalogUrl,
)

fun SkillRunEntity.toDomain(): Insight = Insight(
    id = id,
    docId = docId,
    skillId = skillId,
    status = Insight.Status.of(status),
    result = result,
    error = error,
    startedAt = startedAt,
    finishedAt = finishedAt,
)
