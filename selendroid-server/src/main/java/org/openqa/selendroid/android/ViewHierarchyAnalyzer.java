/*
 * Copyright 2012 selendroid committers.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.openqa.selendroid.android;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openqa.selendroid.ServerInstrumentation;
import org.openqa.selendroid.server.exceptions.SelendroidException;

import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;

public class ViewHierarchyAnalyzer {
  private static final ViewHierarchyAnalyzer INSTANCE = new ViewHierarchyAnalyzer();

  public static ViewHierarchyAnalyzer getDefaultInstance() {
    return INSTANCE;
  }

  public Set<View> getTopLevelViews() {
    try {
      Class<?> wmClass = Class.forName("android.view.WindowManagerImpl");
      Object wm = wmClass.getDeclaredMethod("getDefault").invoke(null);
      Field views = wmClass.getDeclaredField("mViews");
      views.setAccessible(true);
      synchronized (wm) {
        return new HashSet<View>(Arrays.asList(((View[]) views.get(wm)).clone()));
      }
    } catch (Exception exception) {
      throw new SelendroidException("Selendroid only supports Android 2.2", exception);
    }
  }

  public View getRecentDecorView() {
    return getRecentDecorView(getTopLevelViews());
  }

  private View getRecentDecorView(Set<View> views) {
    Collection<View> decorViews = Collections2.filter(views, new DecorViewPredicate());
    View container = null;
    long drawingTime = 0;
    for (View view : decorViews) {
      if (view.isShown() && view.hasWindowFocus() && view.getDrawingTime() > drawingTime) {
        container = view;
        drawingTime = view.getDrawingTime();
      }
    }
    return container;
  }

  private static class DecorViewPredicate implements Predicate<View> {
    @Override
    public boolean apply(View view) {
      return view.getClass().getName()
          .equals("com.android.internal.policy.impl.PhoneWindow$DecorView");
    }
  }

  private void addChildren(ArrayList<View> views, ViewGroup viewGroup) {
    if (viewGroup != null) {
      for (int i = 0; i < viewGroup.getChildCount(); i++) {
        final View child = viewGroup.getChildAt(i);
        views.add(child);
        if (child instanceof ViewGroup) {
          addChildren(views, (ViewGroup) child);
        }
      }
    }
  }

  public Collection<View> getViews() {
    Set<View> views = getTopLevelViews();
    final ArrayList<View> allViews = new ArrayList<View>();
    Collection<View> nonDecorViews =
        Collections2.filter(views, Predicates.not(new DecorViewPredicate()));

    if (views != null && views.size() > 0) {
      for (View view : nonDecorViews) {
        try {
          addChildren(allViews, (ViewGroup) view);
        } catch (Exception ignored) {}
        if (view != null) allViews.add(view);
      }
      View decorView = getRecentDecorView(views);
      try {
        addChildren(allViews, (ViewGroup) decorView);
      } catch (Exception ignored) {}
      if (decorView != null) allViews.add(decorView);
    }
    return allViews;
  }

  public String getNativeId(View view) {
    String id = "";
    try {
      id =
          ServerInstrumentation.getInstance().getCurrentActivity().getResources()
              .getResourceName(view.getId());
    } catch (Resources.NotFoundException e) {
      // can happen
    }
    return id;
  }

  public WebView findWebView() {
    final List<WebView> webViews = new ArrayList<WebView>();
    Collection<View> views = getViews();
    for (View view : views) {
      if (view instanceof WebView) {
        final WebView webview = (WebView) view;
        webViews.add(webview);
      }
    }
    if (webViews.isEmpty()) {
      return null;
    }
    System.out.println("Number of webviews: " + webViews.size());
    return webViews.get(0);
  }



}