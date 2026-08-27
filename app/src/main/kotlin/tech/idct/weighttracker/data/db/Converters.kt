package tech.idct.weighttracker.data.db

import androidx.room.TypeConverter
import tech.idct.weighttracker.domain.EntrySource
import tech.idct.weighttracker.domain.PlanMode
import tech.idct.weighttracker.domain.ThemeChoice
import tech.idct.weighttracker.domain.WeightUnit

class Converters {
    @TypeConverter fun toSource(v: String): EntrySource = EntrySource.valueOf(v)
    @TypeConverter fun fromSource(v: EntrySource): String = v.name

    @TypeConverter fun toMode(v: String): PlanMode = PlanMode.valueOf(v)
    @TypeConverter fun fromMode(v: PlanMode): String = v.name

    @TypeConverter fun toUnit(v: String): WeightUnit = WeightUnit.valueOf(v)
    @TypeConverter fun fromUnit(v: WeightUnit): String = v.name

    @TypeConverter fun toTheme(v: String): ThemeChoice = ThemeChoice.valueOf(v)
    @TypeConverter fun fromTheme(v: ThemeChoice): String = v.name
}
