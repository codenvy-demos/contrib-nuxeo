/*
 * (C) Copyright 2006-2009 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Leroy Merlin (http://www.leroymerlin.fr/) - initial implementation
 */

package org.nuxeo.opensocial.container.client;

import org.nuxeo.opensocial.container.client.bean.GadgetBean;
import org.nuxeo.opensocial.container.client.view.ContainerPortal;
import org.nuxeo.opensocial.container.client.view.GadgetPortlet;
import org.nuxeo.opensocial.container.client.view.SavePreferenceAsyncCallback;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.JsArray;

/**
 * JSNI implementation of opensocial container
 * 
 * @author Guillaume Cusnieux
 */
public class GadgetService {

  /**
   * Registering avaible service
   */
  public static native void registerService()
  /*-{
    var rpc = $wnd.gadgets.rpc;
    rpc.register('resize_iframe', @org.nuxeo.opensocial.container.client.GadgetService::resizeIframe(I));
    rpc.register('set_pref', @org.nuxeo.opensocial.container.client.GadgetService::setPref(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;));
    rpc.register('set_title', @org.nuxeo.opensocial.container.client.GadgetService::setTitle(Ljava/lang/String;));
    rpc.register('show_image', @org.nuxeo.opensocial.container.client.GadgetService::showImage(Lcom/google/gwt/core/client/JsArray;I));

    //Nuxeo specific bindings
    rpc.register('get_nuxeo_space_id', @org.nuxeo.opensocial.container.client.GadgetService::getSpaceId());
  }-*/;

  /**
   * Resize gadget
   * 
   * @param height
   */
  public static native void resizeIframe(int height)
  /*-{
    @org.nuxeo.opensocial.container.client.GadgetService::setHeight(Ljava/lang/String;I)(this.f,height);
  }-*/;

  public static void setHeight(String frameId, int height) {
    ContainerPortal portal = ContainerEntryPoint.getContainerPortal();
    portal.getGadgetPortletByFrameId(frameId)
        .setHeight(height + 20);
    portal.incrementLoading();
  };

  /**
   * Set new preference
   * 
   * @param editToken
   * @param name
   * @param value
   */
  public static native void setPref(String editToken, String name, String value)
  /*-{
    for ( var i = 1, j = arguments.length; i < j; i += 2) {
      if(arguments[i]!="refresh")
        @org.nuxeo.opensocial.container.client.GadgetService::setUserPref(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)(this.f,arguments[i],arguments[i+1]);
    }
    @org.nuxeo.opensocial.container.client.GadgetService::saveUserPref(Ljava/lang/String;)(this.f);
  }-*/;

  public static void saveUserPref(String frameId) {
    GadgetBean bean = ContainerEntryPoint.getContainerPortal()
        .getGadgetPortletByFrameId(frameId)
        .getGadgetBean();
    ContainerEntryPoint.getService()
        .saveGadgetPreferences(bean, null, ContainerEntryPoint.getGwtParams(),
            new SavePreferenceAsyncCallback<GadgetBean>(bean));
  };

  public static void setUserPref(String frameId, String key, String value) {
    ContainerEntryPoint.getContainerPortal()
        .getGadgetPortletByFrameId(frameId)
        .getGadgetBean()
        .setPref(key, value);
  };

  /**
   * Service : Set new title
   * 
   * @param title
   */
  public static native void setTitle(String title)
  /*-{
    @org.nuxeo.opensocial.container.client.GadgetService::setTitleToGadget(Ljava/lang/String;Ljava/lang/String;)(this.f,title);
  }-*/;

  public static void setTitleToGadget(String frameId, String title) {
    if (title != null) {
      GadgetPortlet p = ContainerEntryPoint.getContainerPortal()
          .getGadgetPortletByFrameId(frameId);
      p.setTitle(title);
    }
  };

  /**
   * Service : Show List of image in Container
   * 
   * @param childs
   * @param current
   */
  public static native void showImage(JsArray<JavaScriptObject> childs,
      int current)
  /*-{
    var container = $wnd.jQuery("#gadget-fancy");
        if(container.length == 0){
          container = $wnd.jQuery("<div></div>").attr("id","gadget-fancy");
          $wnd.jQuery($wnd.document.body).append(container);
        }
        container.html("");

        $wnd.jQuery.each(childs, function(index, child) {
           var a = $wnd.jQuery("<a></a>").attr('href',child.path.value+"@@viewVersion?v=Original").attr('class','fancyboxImage').attr('rel','photoGroup');
           container.append(a);
        });

        $wnd.jQuery($wnd.jQuery("a.fancyboxImage")).fancybox({
         'zoomSpeedIn': 500,
         'zoomSpeedOut': 500,
         'overlayShow': false,
         'forceImage': true,
         'hideOnContentClick': false
        });

        $wnd.jQuery($wnd.jQuery("a.fancyboxImage")[current]).click();
  }-*/;

  /**
   * Security : Setter of rpc relay
   * 
   * @param iframeId
   * @param rpcToken
   */
  public static native void setRelayRpc(String iframeId, String serverBase)
  /*-{
    $wnd.gadgets.rpc.setRelayUrl(iframeId, serverBase + "files/container/rpc_relay.html");
  }-*/;

  /**
   * Security : Setter of Auth token
   * 
   * @param iframeId
   * @param rpcToken
   */
  public static native void setAuthToken(String iframeId, String rpcToken)
  /*-{
    $wnd.gadgets.rpc.setAuthToken(iframeId, rpcToken);
  }-*/;

  public static String getSpaceId() {
    ContainerPortal portal = ContainerEntryPoint.getContainerPortal();
    return portal.getContainer()
        .getSpaceId();
  }

}