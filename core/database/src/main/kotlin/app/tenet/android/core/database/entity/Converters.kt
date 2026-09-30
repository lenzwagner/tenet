package app.tenet.android.core.database.entity

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun entryTypeToString(value: EntryType): String = value.name

    @TypeConverter
    fun stringToEntryType(value: String): EntryType = EntryType.valueOf(value)

    @TypeConverter
    fun mealTypeToString(value: MealType): String = value.name

    @TypeConverter
    fun stringToMealType(value: String): MealType = MealType.valueOf(value)

    @TypeConverter
    fun foodSourceToString(value: FoodSource): String = value.name

    @TypeConverter
    fun stringToFoodSource(value: String): FoodSource = FoodSource.valueOf(value)

    @TypeConverter
    fun disciplineToString(value: Discipline): String = value.name

    @TypeConverter
    fun stringToDiscipline(value: String): Discipline = Discipline.valueOf(value)

    @TypeConverter
    fun measureTypeToString(value: MeasureType): String = value.name

    @TypeConverter
    fun stringToMeasureType(value: String): MeasureType = MeasureType.valueOf(value)

    @TypeConverter
    fun setTypeToString(value: SetType): String = value.name

    @TypeConverter
    fun stringToSetType(value: String): SetType = SetType.valueOf(value)

    @TypeConverter
    fun sessionModeToString(value: SessionMode): String = value.name

    @TypeConverter
    fun stringToSessionMode(value: String): SessionMode = SessionMode.valueOf(value)

    @TypeConverter
    fun skillCategoryToString(value: SkillCategory): String = value.name

    @TypeConverter
    fun stringToSkillCategory(value: String): SkillCategory = SkillCategory.valueOf(value)

    @TypeConverter
    fun criterionTypeToString(value: CriterionType): String = value.name

    @TypeConverter
    fun stringToCriterionType(value: String): CriterionType = CriterionType.valueOf(value)

    @TypeConverter
    fun formQualityToString(value: FormQuality): String = value.name

    @TypeConverter
    fun stringToFormQuality(value: String): FormQuality = FormQuality.valueOf(value)

    @TypeConverter
    fun runTypeToString(value: RunType): String = value.name

    @TypeConverter
    fun stringToRunType(value: String): RunType = RunType.valueOf(value)

    @TypeConverter
    fun runSourceToString(value: RunSource): String = value.name

    @TypeConverter
    fun stringToRunSource(value: String): RunSource = RunSource.valueOf(value)

    @TypeConverter
    fun tagKindToString(value: TagKind): String = value.name

    @TypeConverter
    fun stringToTagKind(value: String): TagKind = TagKind.valueOf(value)
}
