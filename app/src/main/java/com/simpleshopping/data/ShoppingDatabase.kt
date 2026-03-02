package com.simpleshopping.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.runBlocking

@Database(
    entities = [Section::class, Item::class, ItemHistory::class, TripSnapshot::class],
    version = 4,
    exportSchema = false
)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun sectionDao(): SectionDao
    abstract fun itemDao(): ItemDao
    abstract fun itemHistoryDao(): ItemHistoryDao
    abstract fun tripSnapshotDao(): TripSnapshotDao

    companion object {
        @Volatile
        private var INSTANCE: ShoppingDatabase? = null

        private val DEFAULT_SECTIONS = listOf(
            "Produce", "Dairy", "Bakery", "Frozen",
            "Meat", "Canned", "Dry", "Beverages",
            "Snacks", "Condiments", "Household", "Other"
        )

        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): ShoppingDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                ShoppingDatabase::class.java,
                "shopping.db"
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .addCallback(PrepopulateCallback())
                .build()
        }

        private val COMMON_FOODS = mapOf(
            "Produce" to listOf(
                "Bananas", "Apples", "Oranges", "Strawberries", "Blueberries",
                "Grapes", "Lemons", "Limes", "Avocados", "Tomatoes",
                "Potatoes", "Onions", "Garlic", "Carrots", "Broccoli",
                "Spinach", "Lettuce", "Peppers", "Cucumbers", "Celery",
                "Mushrooms", "Sweet Potatoes", "Corn", "Green Beans", "Zucchini"
            ),
            "Dairy" to listOf(
                "Milk", "Eggs", "Butter", "Cheddar Cheese", "Mozzarella",
                "Cream Cheese", "Sour Cream", "Yogurt", "Heavy Cream",
                "Parmesan", "Cottage Cheese", "Half & Half"
            ),
            "Bakery" to listOf(
                "Bread", "Bagels", "Tortillas", "Hamburger Buns", "Hot Dog Buns",
                "Croissants", "English Muffins", "Pita Bread", "Dinner Rolls"
            ),
            "Frozen" to listOf(
                "Ice Cream", "Frozen Pizza", "Frozen Vegetables", "Frozen Fruit",
                "Chicken Nuggets", "Fish Sticks", "Waffles", "Frozen Burritos",
                "French Fries", "Popsicles"
            ),
            "Meat" to listOf(
                "Chicken Breast", "Ground Beef", "Bacon", "Sausage", "Pork Chops",
                "Steak", "Ground Turkey", "Ham", "Hot Dogs", "Deli Turkey",
                "Deli Ham", "Salmon", "Shrimp", "Tilapia"
            ),
            "Canned" to listOf(
                "Canned Tomatoes", "Tomato Sauce", "Tomato Paste", "Chicken Broth",
                "Black Beans", "Kidney Beans", "Chickpeas", "Canned Corn",
                "Canned Tuna", "Soup", "Coconut Milk"
            ),
            "Dry" to listOf(
                "Rice", "Pasta", "Cereal", "Cheerios", "Oatmeal",
                "Flour", "Sugar", "Brown Sugar", "Olive Oil", "Vegetable Oil",
                "Peanut Butter", "Jelly", "Salt", "Pepper", "Pancake Mix"
            ),
            "Beverages" to listOf(
                "Coffee", "Tea", "Water", "Juice", "Soda"
            ),
            "Snacks" to listOf(
                "Chips", "Crackers", "Granola Bars"
            ),
            "Condiments" to listOf(
                "Ketchup", "Mustard", "Mayo", "Soy Sauce", "Hot Sauce",
                "Salad Dressing", "Vinegar", "Honey", "Maple Syrup"
            ),
            "Household" to listOf(
                "Paper Towels", "Toilet Paper", "Dish Soap", "Laundry Detergent",
                "Trash Bags", "Aluminum Foil", "Plastic Wrap", "Napkins"
            )
        )

        private class PrepopulateCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    // runBlocking is safe here: Room's onCreate runs on a background thread,
                    // and we must finish prepopulation before any query can execute.
                    runBlocking {
                        val sectionDao = database.sectionDao()
                        val historyDao = database.itemHistoryDao()
                        val sectionIds = mutableMapOf<String, Long>()

                        DEFAULT_SECTIONS.forEachIndexed { index, name ->
                            val id = sectionDao.insert(
                                Section(
                                    name = name,
                                    sortOrder = index,
                                    isDefault = true
                                )
                            )
                            sectionIds[name] = id
                        }

                        COMMON_FOODS.forEach { (sectionName, foods) ->
                            val sectionId = sectionIds[sectionName] ?: return@forEach
                            foods.forEach { foodName ->
                                historyDao.insert(
                                    ItemHistory(
                                        name = foodName,
                                        sectionId = sectionId,
                                        usageCount = 1
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
