/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lealone.sql.mysql;

import org.lealone.sql.mysql.router.LocalRouter;
import org.lealone.sql.mysql.router.Router;

public class RouterHolder {

    private static Router router = LocalRouter.getInstance();

    public static Router getRouter() {
        return router;
    }

    public static void setRouter(Router r) {
        if (r == null)
            throw new NullPointerException("router is null");
        router = r;
    }

}
