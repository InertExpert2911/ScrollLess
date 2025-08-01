package com.example.scrolltrack.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromGroupType(value: LimitGroupType): String {
        return value.name
    }

    @TypeConverter
    fun toGroupType(value: String): LimitGroupType {
        return LimitGroupType.valueOf(value)
    }
}