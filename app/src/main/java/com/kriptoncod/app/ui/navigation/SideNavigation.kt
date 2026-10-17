package com.kriptoncod.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.components.KriptonCodLogo
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Tablet gezinmesi (alt çubuğun yerine): orta genişlikte dar ray, geniş ekranda etiketli kalıcı çekmece.
 * Sağda 1.5dp kenarlık: uygulamanın düğüm/kart diliyle aynı.
 */
@Composable
fun SideNavigation(
    expanded: Boolean,
    isSelected: (Route) -> Boolean,
    onNavigate: (Route) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    Row(Modifier.fillMaxHeight()) {
        if (expanded) {
            PermanentDrawerSheet(
                modifier = Modifier.width(264.dp),
                drawerContainerColor = scheme.surface,
            ) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KriptonCodLogo(logoSize = 40.dp)
                    Text(
                        stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Route.entries.forEach { route ->
                    NavigationDrawerItem(
                        label = { Text(stringResource(route.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = isSelected(route),
                        onClick = { onNavigate(route) },
                        icon = { Icon(route.icon, contentDescription = null) },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = k.nodeFill,
                            unselectedContainerColor = scheme.surface,
                            selectedIconColor = scheme.primary,
                            selectedTextColor = scheme.primary,
                            unselectedIconColor = k.muted,
                            unselectedTextColor = k.muted,
                        ),
                    )
                }
            }
        } else {
            NavigationRail(
                containerColor = scheme.surface,
                header = {
                    Spacer(Modifier.height(8.dp))
                    KriptonCodLogo(logoSize = 40.dp)
                    Spacer(Modifier.height(8.dp))
                },
            ) {
                Route.entries.forEach { route ->
                    NavigationRailItem(
                        selected = isSelected(route),
                        onClick = { onNavigate(route) },
                        icon = { Icon(route.icon, contentDescription = null) },
                        label = { Text(stringResource(route.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        alwaysShowLabel = true,
                        colors = NavigationRailItemDefaults.colors(
                            indicatorColor = k.nodeFill,
                            selectedIconColor = scheme.primary,
                            selectedTextColor = scheme.primary,
                            unselectedIconColor = k.muted,
                            unselectedTextColor = k.muted,
                        ),
                    )
                }
            }
        }
        VerticalDivider(thickness = 1.5.dp, color = k.nodeBorder)
    }
}
