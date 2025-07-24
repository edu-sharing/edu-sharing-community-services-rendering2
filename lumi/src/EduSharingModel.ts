import { ObjectId } from "mongodb";

/**
 * Represents a model for EduSharing with metadata about nodes and content.
 */
export default class EduSharingModel {
    constructor(public nodeId: string, public contentId: string, public id?: ObjectId) {}
}
