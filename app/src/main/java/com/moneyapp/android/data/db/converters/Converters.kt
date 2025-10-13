package com.moneyapp.android.data.db.converters

import androidx.room.TypeConverter
import com.moneyapp.android.data.db.entities.CategoryType

class CategoryTypeConverter {
    @TypeConverter fun toString(t: CategoryType): String = t.name
    @TypeConverter fun fromString(v: String): CategoryType = CategoryType.valueOf(v)
}
