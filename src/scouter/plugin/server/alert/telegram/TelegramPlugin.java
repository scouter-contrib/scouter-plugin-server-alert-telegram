/*
 *  Copyright 2016 Scouter Project.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); 
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License. 
 *  
 *  @author Sang-Cheon Park
 */
package scouter.plugin.server.alert.telegram;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import scouter.lang.AlertLevel;
import scouter.lang.pack.AlertPack;
import scouter.lang.pack.ObjectPack;
import scouter.lang.plugin.PluginConstants;
import scouter.lang.plugin.annotation.ServerPlugin;
import scouter.server.Configure;
import scouter.server.Logger;
import scouter.server.core.AgentManager;

/**
 * Scouter server plugin to send alert via telegram
 * 
 * @author Sang-Cheon Park(nices96@gmail.com) on 2016. 3. 28.
 */
public class TelegramPlugin {
	
	// Get singleton Configure instance from server
    final Configure conf = Configure.getInstance();

    @ServerPlugin(PluginConstants.PLUGIN_SERVER_ALERT)
    public void alert(final AlertPack pack) {
        if (conf.getBoolean("ext_plugin_telegram_send_alert", false)) {
        	
        	// Get log level (0 : INFO, 1 : WARN, 2 : ERROR, 3 : FATAL)
        	int level = conf.getInt("ext_plugin_telegram_level", 0);
        	
        	if (level <= pack.level) {
        		new Thread() {
        			public void run() {
                        try {
                        	// Get server configurations for telegram
                            String token = conf.getValue("ext_plugin_telegram_bot_token");
                            String chatId = conf.getValue("ext_plugin_telegram_chat_id");
                            
                            assert token != null;
                            assert chatId != null;
                        
                            // Make a request URL using telegram bot api
                            String url = "https://api.telegram.org/bot" + token + "/sendMessage";

                        	// Get the agent Name
                        	String name = AgentManager.getAgentName(pack.objHash) == null ? "N/A" : AgentManager.getAgentName(pack.objHash);
                        	
                        	if (name.equals("N/A") && pack.message.endsWith("connected.")) {
                    			int idx = pack.message.indexOf("connected");
                        		if (pack.message.indexOf("reconnected") > -1) {
                        			name = pack.message.substring(0, idx - 6);
                        		} else {
                        			name = pack.message.substring(0, idx - 4);
                        		}
                        	}
                            
                            String title = pack.title;
                            String msg = pack.message;
                            if (title.equals("INACTIVE_OBJECT")) {
                            	title = "An object has been inactivated.";
                            	msg = pack.message.substring(0, pack.message.indexOf("OBJECT") - 1);
                            }
                          
                        	// Make message contents
                            String contents = "[TYPE] : " + pack.objType.toUpperCase() + "\n" + 
                                           	  "[NAME] : " + name + "\n" + 
                                              "[LEVEL] : " + AlertLevel.getName(pack.level) + "\n" +
                                              "[TITLE] : " + title + "\n" + 
                                              "[MESSAGE] : " + msg;
                          
                            Message message = new Message(chatId, contents);
                            String param = new Gson().toJson(message);
                  
                            HttpPost post = new HttpPost(url);
                            post.addHeader("Content-Type","application/json");
                            post.setEntity(new StringEntity(param));
                          
                            CloseableHttpClient client = HttpClientBuilder.create().build();
                          
                            // send the post request
                            HttpResponse response = client.execute(post);
                            
                            if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                                println("Telegram message sent to [" + chatId + "] successfully.");
                            } else {
                                println("Telegram message sent failed. Verify below information.");
                                println("[URL] : " + url);
                                println("[Message] : " + param);
                                println("[Reason] : " + EntityUtils.toString(response.getEntity(), "UTF-8"));
                            }
                        } catch (Exception e) {
                        	println("[Error] : " + e.getMessage());
                        	
                        	if(conf._trace) {
                                e.printStackTrace();
                            }
                        }
        			}
        		}.start();
            }
        }
    }
    
    @ServerPlugin(PluginConstants.PLUGIN_SERVER_OBJECT)
	public void object(ObjectPack pack) {
		if (pack.version != null && pack.version.length() > 0) {
			AlertPack p = null;
			if (pack.wakeup == 0L) {
				// in case of new agent connected
				p = new AlertPack();
		        p.level = AlertLevel.INFO;
		        p.objHash = pack.objHash;
		        p.title = "An object has been activated.";
		        p.message = pack.objName + " is connected.";
		        p.time = System.currentTimeMillis();
		        p.objType = "scouter";
				
		        alert(p);
			} else if (pack.alive == false) {
				// in case of agent reconnected
				p = new AlertPack();
		        p.level = AlertLevel.INFO;
		        p.objHash = pack.objHash;
		        p.title = "An object has been activated.";
		        p.message = pack.objName + " is reconnected.";
		        p.time = System.currentTimeMillis();
		        p.objType = "scouter";
				
		        alert(p);
			}
			
			// inactive state can be handled in alert() method.
		}
	}

    private void println(Object o) {
        if (conf.getBoolean("ext_plugin_telegram_debug", false)) {
            Logger.println(o);
        }
    }
}