package com.example.notetakerfrontend

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONArray
import org.json.JSONObject

data class Note(var id: Long, var title: String, var content: String)

/**
 * A minimalist note-taking UI.
 *
 * Features:
 *  - List/search notes in a RecyclerView.
 *  - Create/edit/delete notes using a modal dialog.
 *  - Local storage (SharedPreferences/JSON).
 *  - Floating action button for note creation.
 *  - Light, modern theme with custom colors.
 */
// PUBLIC_INTERFACE
class MainActivity : AppCompatActivity() {

    private val PREFS_NAME = "notes_prefs"
    private val NOTES_KEY = "notes"
    private val NOTE_ID_KEY = "note_id_next"

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotesAdapter
    private lateinit var searchEditText: EditText
    private lateinit var fab: FloatingActionButton
    private var notes: MutableList<Note> = mutableListOf()
    private var filteredNotes: MutableList<Note> = mutableListOf()
    private var nextId: Long = 1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        setContentView(R.layout.activity_main)

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        recyclerView = findViewById(R.id.recyclerViewNotes)
        fab = findViewById(R.id.fab)
        searchEditText = findViewById(R.id.editTextSearch)

        adapter = NotesAdapter(filteredNotes,
            onNoteLongClick = { note -> showDeleteDialog(note) },
            onNoteClick = { note -> showNoteDialog(editMode = true, note = note) }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        fab.setOnClickListener { showNoteDialog(editMode = false) }
        searchEditText.addTextChangedListener { filterNotes(it?.toString() ?: "") }

        loadNotes()
        updateNotesView()
    }

    private fun showNoteDialog(editMode: Boolean, note: Note? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_note, null)
        val titleInput = dialogView.findViewById<EditText>(R.id.editTitle)
        val contentInput = dialogView.findViewById<EditText>(R.id.editContent)
        if (editMode && note != null) {
            titleInput.setText(note.title)
            contentInput.setText(note.content)
        }
        val builder = AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle(if (editMode) "Edit Note" else "New Note")
            .setView(dialogView)
            .setPositiveButton(if (editMode) "Save" else "Add", null)
            .setNegativeButton("Cancel", null)

        val dialog = builder.create()
        dialog.setOnShowListener {
            val addBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            addBtn.setOnClickListener {
                val title = titleInput.text.toString().trim()
                val content = contentInput.text.toString().trim()
                if (title.isEmpty() && content.isEmpty()) {
                    Toast.makeText(this, "Note cannot be blank", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (editMode && note != null) {
                    note.title = title
                    note.content = content
                } else {
                    notes.add(0, Note(generateNoteId(), title, content))
                }
                saveNotes()
                filterNotes(searchEditText.text.toString())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(note: Note) {
        AlertDialog.Builder(this, R.style.ModernDialog)
            .setTitle("Delete note?")
            .setMessage("Are you sure you want to permanently delete this note?")
            .setPositiveButton("Delete") { _, _ ->
                notes.removeAll { it.id == note.id }
                saveNotes()
                filterNotes(searchEditText.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadNotes() {
        notes.clear()
        val jsonString = sharedPrefs.getString(NOTES_KEY, null)
        nextId = sharedPrefs.getLong(NOTE_ID_KEY, 1L)
        if (!jsonString.isNullOrBlank()) {
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                notes.add(
                    Note(
                        id = obj.getLong("id"),
                        title = obj.getString("title"),
                        content = obj.getString("content")
                    )
                )
            }
        }
        filteredNotes.clear()
        filteredNotes.addAll(notes)
    }

    private fun saveNotes() {
        val arr = JSONArray()
        for (n in notes) {
            val obj = JSONObject()
            obj.put("id", n.id)
            obj.put("title", n.title)
            obj.put("content", n.content)
            arr.put(obj)
        }
        sharedPrefs.edit()
            .putString(NOTES_KEY, arr.toString())
            .putLong(NOTE_ID_KEY, nextId)
            .apply()
    }

    private fun filterNotes(query: String) {
        filteredNotes.clear()
        if (query.isBlank()) {
            filteredNotes.addAll(notes)
        } else {
            val q = query.trim().lowercase()
            filteredNotes.addAll(
                notes.filter {
                    it.title.lowercase().contains(q) ||
                            it.content.lowercase().contains(q)
                }
            )
        }
        updateNotesView()
    }

    private fun updateNotesView() {
        adapter.notifyDataSetChanged()
        findViewById<View>(R.id.textNoNotes).visibility =
            if (filteredNotes.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun generateNoteId(): Long {
        nextId += 1
        return nextId - 1
    }

    // PUBLIC_INTERFACE
    class NotesAdapter(
        private val notes: List<Note>,
        private val onNoteLongClick: (Note) -> Unit,
        private val onNoteClick: (Note) -> Unit
    ) : RecyclerView.Adapter<NotesAdapter.NoteViewHolder>() {

        inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.textTitle)
            val content: TextView = itemView.findViewById(R.id.textContent)
            init {
                itemView.setOnLongClickListener {
                    onNoteLongClick(notes[adapterPosition])
                    true
                }
                itemView.setOnClickListener {
                    onNoteClick(notes[adapterPosition])
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_note, parent, false)
            return NoteViewHolder(view)
        }

        override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
            val note = notes[position]
            holder.title.text = note.title.ifBlank { "(No Title)" }
            holder.content.text = note.content
        }

        override fun getItemCount(): Int = notes.size
    }
}
