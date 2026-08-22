package com.lieyabull.dung.shop;

/** Which shop a transaction belongs to — determines the currency charged for rolls. */
public enum ShopType {
    /** The in-run SHOP room: purchases are charged run coins ({@code PlayerState.coins}). */
    RUN,
    /** The between-run persistent shop: purchases are charged persistent coins
     *  ({@code MetaProfile.persistentCoins}) and items are marked persistent. */
    PERSISTENT
}