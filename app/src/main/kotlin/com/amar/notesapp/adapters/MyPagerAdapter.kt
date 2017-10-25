package com.amar.NoteDirector.adapters

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.PagerAdapter
import android.view.ViewGroup
import com.amar.NoteDirector.activities.ViewPagerActivity
import com.amar.NoteDirector.fragments.PhotoFragment
import com.amar.NoteDirector.fragments.VideoFragment
import com.amar.NoteDirector.fragments.ViewPagerFragment
import com.amar.NoteDirector.helpers.MEDIUM
import com.amar.NoteDirector.models.Medium

class MyPagerAdapter(val activity: ViewPagerActivity, fm: FragmentManager, val media: MutableList<Medium>) : FragmentStatePagerAdapter(fm) {
    private val mFragments = HashMap<Int, ViewPagerFragment>()
    override fun getCount() = media.size

    override fun getItem(position: Int): Fragment {
        val medium = media[position]
        val bundle = Bundle()
        bundle.putSerializable(MEDIUM, medium)
        val fragment: ViewPagerFragment

        if (medium.video) {
            fragment = VideoFragment()
        } else {
            fragment = PhotoFragment()
        }

        fragment.arguments = bundle
        fragment.listener = activity
        return fragment
    }

    override fun getItemPosition(item: Any?) = PagerAdapter.POSITION_NONE

    override fun instantiateItem(container: ViewGroup?, position: Int): Any {
        val fragment = super.instantiateItem(container, position) as ViewPagerFragment
        mFragments.put(position, fragment)
        return fragment
    }

    override fun destroyItem(container: ViewGroup?, position: Int, any: Any?) {
        mFragments.remove(position)
        super.destroyItem(container, position, any)
    }

    fun getCurrentFragment(position: Int) = mFragments.get(position)

    fun toggleFullscreen(isFullscreen: Boolean) {
        for ((pos, fragment) in mFragments) {
            fragment.fullscreenToggled(isFullscreen)
        }
    }
}
