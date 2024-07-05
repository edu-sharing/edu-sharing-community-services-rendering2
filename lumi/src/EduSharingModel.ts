import { ObjectId } from "mongodb";

export default class EduSharingModel {
    constructor(public nodeId: string, public contentId: string, public id?: ObjectId) {}
}