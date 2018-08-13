package com.example.edward.chatexercise

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.bumptech.glide.Glide
import kotlinx.android.synthetic.main.item_message.view.*


/**
 *   Created by Edward on 8/13/2018.
 */
class MessageAdapter(
        context: Context,
        resource: Int,
        messages: List<FriendlyMessage>
) : ArrayAdapter<FriendlyMessage>(context, resource, messages) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {

        val view = convertView ?:(context as Activity).layoutInflater
                .inflate(R.layout.item_message, parent, false)

        val message = getItem(position)

        with(view){
            if (message.photoUrl != null){
                messageTextView.visibility = View.GONE
                photoImageView.visibility = View.VISIBLE
                Glide.with(context).load(message.photoUrl).into(photoImageView)
            } else {
                messageTextView.visibility = View.VISIBLE
                messageTextView.text = message.text
                photoImageView.visibility = View.GONE
            }
            nameTextView.text = message.name
        }

        return view
    }
}