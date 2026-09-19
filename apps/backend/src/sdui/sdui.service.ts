import { Injectable, NotFoundException } from '@nestjs/common';
import { TripsService } from '../trips/trips.service';
import { DestinationsService } from '../destinations/destinations.service';
import { ItinerariesService } from '../itineraries/itineraries.service';
import { PlaceService } from '../places/places.service';
import { SDUIScreenResponse, SDUISection } from '@trippin/api-contracts';

@Injectable()
export class SDUIService {
  constructor(
    private readonly tripsService: TripsService,
    private readonly destinationsService: DestinationsService,
    private readonly itinerariesService: ItinerariesService,
    private readonly placeService: PlaceService
  ) {}

  /**
   * Generates dynamic Server-Driven UI (SDUI) for Home screen
   */
  async getHomeScreen(userId: string): Promise<SDUIScreenResponse> {
    const feed = await this.tripsService.getHomeFeed(userId);
    const sections: SDUISection[] = [];

    // Section 1: Header Greeting Widget
    sections.push({
      id: 'section_header_greeting',
      type: 'HEADER_GREETING',
      orderIndex: 0,
      payload: {
        greeting: `Hi, ${feed.user.displayName}`,
        headline: 'Where will you go?',
        userAvatarUrl: feed.user.photoUrl,
        userInitials: feed.user.displayName.split(' ').map((n) => n[0]).join('')
      }
    });

    // Section 2: Search Bar Widget
    sections.push({
      id: 'section_search_bar',
      type: 'SEARCH_BAR',
      orderIndex: 1,
      action: { type: 'NAVIGATE', target: 'explore' },
      payload: {
        placeholder: 'Search places, cities, attractions...'
      }
    });

    // Section 3: AI Hero Banner Widget (Primary CTA)
    sections.push({
      id: 'section_hero_banner',
      type: 'HERO_BANNER',
      orderIndex: 2,
      action: { type: 'NAVIGATE', target: 'planner' },
      payload: {
        badge: 'AI POWERED',
        title: 'Generate Your Custom Itinerary',
        description: 'Physics-checked schedule with opening hours, transit matrix, and weather adaptation in seconds.',
        ctaText: 'Plan a Trip',
        themeColor: '#0D6EFD'
      }
    });

    // Section 4: Quick Actions Grid Widget
    sections.push({
      id: 'section_quick_actions',
      type: 'QUICK_ACTIONS',
      orderIndex: 3,
      payload: {
        actions: [
          { id: 'act_weather', label: 'Weather', icon: 'WbSunny', route: 'weather' },
          { id: 'act_explore', label: 'Explore', icon: 'Explore', route: 'explore' },
          { id: 'act_trips', label: 'My Trips', icon: 'CardTravel', route: 'trips' }
        ]
      }
    });

    // Section 5: Popular Destinations Carousel Widget
    sections.push({
      id: 'section_popular_destinations',
      type: 'HORIZONTAL_CAROUSEL',
      orderIndex: 4,
      title: 'Popular Destinations',
      subtitle: 'Trending worldwide getaways',
      payload: {
        cardStyle: 'DESTINATION_CARD',
        items: feed.popularDestinations.map((d) => ({
          id: d.id,
          title: d.name,
          subtitle: d.country,
          imageUrl: d.imageUrl,
          rating: d.averageRating,
          action: { type: 'NAVIGATE', target: 'planner', params: { destination: d.name } }
        }))
      }
    });

    // Section 6: Recent Trips List Widget
    if (feed.recentTrips.length > 0) {
      sections.push({
        id: 'section_recent_trips',
        type: 'RECENT_TRIPS_LIST',
        orderIndex: 5,
        title: 'Recent Trips',
        payload: {
          trips: feed.recentTrips.map((t) => ({
            id: t.id,
            destination: t.destination,
            dates: `${t.startDate} - ${t.endDate}`,
            status: t.status,
            activitiesCount: t.totalActivitiesCount,
            action: { type: 'NAVIGATE', target: `itinerary/${t.id}` }
          }))
        }
      });
    }

    return {
      screenId: 'home',
      title: 'Home Feed',
      sections
    };
  }

  /**
   * Generates dynamic Server-Driven UI (SDUI) for Itinerary screen
   */
  async getItineraryScreen(tripId: string): Promise<SDUIScreenResponse> {
    const tripDetails = await this.tripsService.getTripDetails(tripId);
    const itinerary = tripDetails.itinerary;

    if (!itinerary) {
      throw new NotFoundException(`No verified itinerary found for trip ${tripId}`);
    }

    const sections: SDUISection[] = [];

    // Weather banner
    sections.push({
      id: 'section_weather_banner',
      type: 'WEATHER_BANNER',
      orderIndex: 0,
      payload: {
        temperatureCelsius: 19,
        condition: 'SUNNY',
        advisory: 'Optimal weather for outdoor walking and historic museums.'
      }
    });

    // Days with Timeline Activity Widgets
    itinerary.days.forEach((day, dayIdx) => {
      sections.push({
        id: `section_day_${day.dayIndex}`,
        type: 'TIMELINE_DAY',
        orderIndex: dayIdx + 1,
        title: `Day ${day.dayIndex} (${day.date})`,
        subtitle: day.summary,
        payload: {
          dayIndex: day.dayIndex,
          date: day.date,
          activities: day.activities.map((a) => ({
            id: a.id,
            title: a.title,
            time: `${a.startTime} - ${a.endTime}`,
            durationMinutes: a.durationMinutes,
            transitFromPrevious: a.travelTimeFromPreviousMinutes
              ? `${a.travelTimeFromPreviousMinutes}m via ${a.transitModeFromPrevious || 'Transit'}`
              : null,
            placeId: a.placeId,
            reason: a.reason,
            bookingUrl: a.bookingUrl
          }))
        }
      });
    });

    // Conversational AI Edit Floating Banner
    sections.push({
      id: 'section_ai_edit_cta',
      type: 'CALL_TO_ACTION',
      orderIndex: 99,
      action: { type: 'OPEN_MODAL', target: 'ai_modify_dialog' },
      payload: {
        title: 'Modify with AI',
        subtitle: 'Tap to adapt pace, change venues, or shift days conversationally.'
      }
    });

    return {
      screenId: `itinerary_${tripId}`,
      title: `${tripDetails.trip.destination} Itinerary (v${itinerary.version})`,
      sections
    };
  }
}
