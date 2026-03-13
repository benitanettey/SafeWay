package com.example.safeway.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ContactDao {

    @Insert
    suspend fun insertContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("SELECT * FROM contacts")
    suspend fun getAllContacts(): List<Contact>

    @Query("SELECT * FROM contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: Int): Contact?

    @Query("UPDATE contacts SET name = :name, phone = :phone, relationship = :relationship WHERE id = :id")
    suspend fun updateContact(id: Int, name: String, phone: String, relationship: String)

    @Query("SELECT * FROM contacts WHERE smsAlerts = 1")
    suspend fun getContactsWithSmsAlerts(): List<Contact>
}

